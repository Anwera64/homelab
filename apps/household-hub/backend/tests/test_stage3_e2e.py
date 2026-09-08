import asyncio
import json
import pytest
from typing import AsyncGenerator
import httpx

from app.domain.entities.llm_message import LLMResponse, LLMResponseChunk
from app.bootstrap.di import _ollama_connector
from app.main import app


async def setup_stage3_environment(client: httpx.AsyncClient) -> tuple[str, str, str, str, str]:
    """Helper to setup admin, member1, member2, and return tokens + agent_id."""
    admin_reg = await client.post(
        "/api/v1/auth/register-initial",
        json={
            "username": "admin",
            "email": "admin@homelab.local",
            "password": "Password123!",
            "full_name": "Admin User",
        },
    )
    admin_token = admin_reg.json()["access_token"]

    # Member 1
    await client.post(
        "/api/v1/users",
        json={
            "username": "alex",
            "email": "alex@homelab.local",
            "password": "Password123!",
            "full_name": "Alex Member",
        },
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    login1 = await client.post(
        "/api/v1/auth/login",
        json={"username": "alex", "password": "Password123!"},
    )
    member1_token = login1.json()["access_token"]

    # Member 2
    await client.post(
        "/api/v1/users",
        json={
            "username": "maria",
            "email": "maria@homelab.local",
            "password": "Password123!",
            "full_name": "Maria Member",
        },
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    login2 = await client.post(
        "/api/v1/auth/login",
        json={"username": "maria", "password": "Password123!"},
    )
    member2_token = login2.json()["access_token"]

    # Get assistant agent
    agent_resp = await client.get("/api/v1/agents/assistant", headers={"Authorization": f"Bearer {member1_token}"})
    agent_id = agent_resp.json()["id"]

    return admin_token, member1_token, member2_token, agent_id, agent_resp.json()["name"]


@pytest.mark.asyncio
async def test_stage3_e2e_chat_turn(client: httpx.AsyncClient, monkeypatch):
    """Verify full end-to-end multi-turn conversation and persistence."""
    _, member1_token, _, agent_id, _ = await setup_stage3_environment(client)

    # Mock Ollama connector
    async def fake_chat_completion(messages, model, temperature=0.7, top_p=0.9, tools=None):
        return LLMResponse(content="Hello Alex! How can I assist you today?")

    monkeypatch.setattr(_ollama_connector, "chat_completion", fake_chat_completion)

    # 1. Create conversation session
    sess_resp = await client.post(
        "/api/v1/sessions",
        json={"agent_id": agent_id, "title": "Stage 3 Testing"},
        headers={"Authorization": f"Bearer {member1_token}"},
    )
    session_id = sess_resp.json()["id"]

    # 2. Execute chat turn
    chat_resp = await client.post(
        f"/api/v1/sessions/{session_id}/chat",
        json={"content": "Hello assistant!"},
        headers={"Authorization": f"Bearer {member1_token}"},
    )
    assert chat_resp.status_code == 200
    data = chat_resp.json()
    assert data["message"]["role"] == "assistant"
    assert data["message"]["content"] == "Hello Alex! How can I assist you today?"
    assert data["suggest_secret_mode"] is False

    # 3. Verify messages persisted in session
    hist_resp = await client.get(
        f"/api/v1/sessions/{session_id}",
        headers={"Authorization": f"Bearer {member1_token}"},
    )
    assert hist_resp.status_code == 200
    messages = hist_resp.json()["messages"]
    assert len(messages) == 2
    assert messages[0]["role"] == "user"
    assert messages[0]["content"] == "Hello assistant!"
    assert messages[1]["role"] == "assistant"
    assert messages[1]["content"] == "Hello Alex! How can I assist you today?"


@pytest.mark.asyncio
async def test_stage3_e2e_privacy_trigger_and_secret_mode(client: httpx.AsyncClient, monkeypatch):
    """Verify natural language cues trigger turn-level privacy and secret mode recommendation."""
    _, member1_token, _, agent_id, _ = await setup_stage3_environment(client)

    async def fake_chat_completion(messages, model, temperature=0.7, top_p=0.9, tools=None):
        return LLMResponse(content="Understood, I will keep this confidential.")

    monkeypatch.setattr(_ollama_connector, "chat_completion", fake_chat_completion)

    sess_resp = await client.post(
        "/api/v1/sessions",
        json={"agent_id": agent_id, "title": "Gift Planning", "is_secret": False},
        headers={"Authorization": f"Bearer {member1_token}"},
    )
    session_id = sess_resp.json()["id"]

    chat_resp = await client.post(
        f"/api/v1/sessions/{session_id}/chat",
        json={"content": "Keep this between us, I am buying a gift for Maria."},
        headers={"Authorization": f"Bearer {member1_token}"},
    )
    assert chat_resp.status_code == 200
    data = chat_resp.json()
    assert data["suggest_secret_mode"] is True


@pytest.mark.asyncio
async def test_stage3_e2e_session_concurrency_lock(client: httpx.AsyncClient, monkeypatch):
    """Verify concurrent requests to the same session are rejected with 409 Conflict."""
    _, member1_token, _, agent_id, _ = await setup_stage3_environment(client)

    async def delayed_chat_completion(messages, model, temperature=0.7, top_p=0.9, tools=None):
        await asyncio.sleep(0.3)
        return LLMResponse(content="Delayed response")

    monkeypatch.setattr(_ollama_connector, "chat_completion", delayed_chat_completion)

    sess_resp = await client.post(
        "/api/v1/sessions",
        json={"agent_id": agent_id, "title": "Concurrency Test"},
        headers={"Authorization": f"Bearer {member1_token}"},
    )
    session_id = sess_resp.json()["id"]

    # Send 2 parallel chat requests to the same session
    res1, res2 = await asyncio.gather(
        client.post(
            f"/api/v1/sessions/{session_id}/chat",
            json={"content": "Message 1"},
            headers={"Authorization": f"Bearer {member1_token}"},
        ),
        client.post(
            f"/api/v1/sessions/{session_id}/chat",
            json={"content": "Message 2"},
            headers={"Authorization": f"Bearer {member1_token}"},
        ),
        return_exceptions=True,
    )

    statuses = {res1.status_code, res2.status_code}
    assert 200 in statuses
    assert 409 in statuses


@pytest.mark.asyncio
async def test_stage3_e2e_zero_leak_isolation(client: httpx.AsyncClient):
    """Verify Member 2 cannot chat in Member 1's private conversation session."""
    _, member1_token, member2_token, agent_id, _ = await setup_stage3_environment(client)

    sess_resp = await client.post(
        "/api/v1/sessions",
        json={"agent_id": agent_id, "title": "Alex Private Chat"},
        headers={"Authorization": f"Bearer {member1_token}"},
    )
    session_id = sess_resp.json()["id"]

    # Member 2 attempts to chat in Member 1's session
    hack_resp = await client.post(
        f"/api/v1/sessions/{session_id}/chat",
        json={"content": "I am snooping!"},
        headers={"Authorization": f"Bearer {member2_token}"},
    )
    assert hack_resp.status_code == 403
    assert "Zero-Leak" in hack_resp.json()["detail"]


@pytest.mark.asyncio
async def test_stage3_e2e_chat_stream(client: httpx.AsyncClient, monkeypatch):
    """Verify Server-Sent Events (SSE) streaming chat endpoint."""
    _, member1_token, _, agent_id, _ = await setup_stage3_environment(client)

    async def fake_stream(messages, model, temperature=0.7, top_p=0.9, tools=None):
        yield LLMResponseChunk(delta_content="Hello ")
        yield LLMResponseChunk(delta_content="from ")
        yield LLMResponseChunk(delta_content="stream!")

    async def fake_chat(messages, model, temperature=0.7, top_p=0.9, tools=None):
        return LLMResponse(content="Hello from stream!")

    monkeypatch.setattr(_ollama_connector, "stream_chat_completion", fake_stream)
    monkeypatch.setattr(_ollama_connector, "chat_completion", fake_chat)

    sess_resp = await client.post(
        "/api/v1/sessions",
        json={"agent_id": agent_id, "title": "Streaming Test"},
        headers={"Authorization": f"Bearer {member1_token}"},
    )
    session_id = sess_resp.json()["id"]

    stream_resp = await client.post(
        f"/api/v1/sessions/{session_id}/chat/stream",
        json={"content": "Stream to me"},
        headers={"Authorization": f"Bearer {member1_token}"},
    )
    assert stream_resp.status_code == 200
    assert "text/event-stream" in stream_resp.headers["content-type"]

    body = stream_resp.text
    assert "data: [DONE]" in body
    assert "stream!" in body


@pytest.mark.asyncio
async def test_stage3_e2e_gossip_bus_lifecycle(client: httpx.AsyncClient):
    """Verify gossip milestone publication, household visibility, audit, and revocation."""
    admin_token, member1_token, member2_token, _, _ = await setup_stage3_environment(client)

    # 1. Member 1 publishes milestone
    create_resp = await client.post(
        "/api/v1/gossip",
        json={
            "category": "academic_deadline",
            "summary": "UPC Thesis Jury presentation on Friday",
            "target_scope": "household",
            "details_json": {"time": "10:00 AM"},
        },
        headers={"Authorization": f"Bearer {member1_token}"},
    )
    assert create_resp.status_code == 201
    milestone_id = create_resp.json()["id"]
    assert create_resp.json()["summary"] == "UPC Thesis Jury presentation on Friday"
    assert create_resp.json()["source_username"] == "alex"

    # 2. Member 2 can view it on the household gossip feed
    feed_resp = await client.get(
        "/api/v1/gossip/household",
        headers={"Authorization": f"Bearer {member2_token}"},
    )
    assert feed_resp.status_code == 200
    feed = feed_resp.json()
    assert any(m["id"] == milestone_id for m in feed)

    # 3. Member 1 can view it in their audit log
    audit_resp = await client.get(
        "/api/v1/gossip/audit",
        headers={"Authorization": f"Bearer {member1_token}"},
    )
    assert audit_resp.status_code == 200
    assert any(m["id"] == milestone_id for m in audit_resp.json())

    # 4. Member 2 cannot revoke Member 1's milestone (must be owner or admin)
    forbidden_revoke = await client.delete(
        f"/api/v1/gossip/{milestone_id}",
        headers={"Authorization": f"Bearer {member2_token}"},
    )
    assert forbidden_revoke.status_code == 403

    # 5. Member 1 can revoke their own milestone
    revoke_resp = await client.delete(
        f"/api/v1/gossip/{milestone_id}",
        headers={"Authorization": f"Bearer {member1_token}"},
    )
    assert revoke_resp.status_code == 200
    assert revoke_resp.json()["message"] == "Gossip milestone revoked successfully"

    # 6. Once revoked, it disappears from the active household feed
    feed_after = await client.get(
        "/api/v1/gossip/household",
        headers={"Authorization": f"Bearer {member2_token}"},
    )
    assert all(m["id"] != milestone_id for m in feed_after.json())
