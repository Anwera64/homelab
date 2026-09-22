"""
What a Chats row needs to draw itself.

The list used to return `agent_id` and nothing else about the agent, and nothing at all about the
conversation's last message — so a row on the phone had a uuid where its emoji, its agent's name
and its preview line should be. These are the fields that fix that, resolved by the hub so the
list arrives complete in one response.
"""

import pytest
import httpx

from tests.auth_helpers import register_admin


async def _member_and_agent(client: httpx.AsyncClient, slug: str = "assistant") -> tuple[str, str]:
    token, _ = await register_admin(client, full_name="Admin")
    agent = await client.get(f"/api/v1/agents/{slug}", headers={"Authorization": f"Bearer {token}"})
    return token, agent.json()["id"]


async def _create_session(client: httpx.AsyncClient, token: str, agent_id: str, title: str) -> str:
    response = await client.post(
        "/api/v1/sessions",
        json={"agent_id": agent_id, "title": title, "is_secret": False},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 201
    return response.json()["id"]


async def _say(client: httpx.AsyncClient, token: str, session_id: str, content: str, role: str = "user") -> None:
    response = await client.post(
        f"/api/v1/sessions/{session_id}/messages",
        json={"role": role, "content": content},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 201


@pytest.mark.asyncio
async def test_a_row_carries_its_agents_name_and_emoji(client: httpx.AsyncClient):
    """GIVEN a session WHEN the list is read THEN the agent's name and avatar come with it."""
    token, agent_id = await _member_and_agent(client)
    await _create_session(client, token, agent_id, "Saturday dinner")

    listed = await client.get("/api/v1/sessions", headers={"Authorization": f"Bearer {token}"})

    assert listed.status_code == 200
    row = listed.json()[0]
    assert row["agent_name"] == "Home & Life Coordinator"
    assert row["agent_avatar"] == "🏡"


@pytest.mark.asyncio
async def test_the_preview_is_the_newest_message(client: httpx.AsyncClient):
    """GIVEN several messages WHEN the list is read THEN the preview is the last one written."""
    token, agent_id = await _member_and_agent(client)
    session_id = await _create_session(client, token, agent_id, "Saturday dinner")

    await _say(client, token, session_id, "Can you put dinner in the calendar for Saturday?")
    await _say(client, token, session_id, "Added. You're both out Saturday evening.", role="assistant")

    listed = await client.get("/api/v1/sessions", headers={"Authorization": f"Bearer {token}"})

    assert listed.json()[0]["last_message_preview"] == "Added. You're both out Saturday evening."


@pytest.mark.asyncio
async def test_a_long_message_is_truncated_by_the_hub(client: httpx.AsyncClient):
    """GIVEN a long message WHEN the list is read THEN the phone is not sent the whole thing."""
    token, agent_id = await _member_and_agent(client)
    session_id = await _create_session(client, token, agent_id, "Jury panel references")
    await _say(client, token, session_id, "x" * 500)

    listed = await client.get("/api/v1/sessions", headers={"Authorization": f"Bearer {token}"})

    preview = listed.json()[0]["last_message_preview"]
    assert len(preview) <= 120
    assert preview.endswith("…")


@pytest.mark.asyncio
async def test_a_conversation_with_nothing_said_in_it_has_no_preview(client: httpx.AsyncClient):
    """GIVEN an empty session WHEN the list is read THEN the preview is null, not an empty string."""
    token, agent_id = await _member_and_agent(client)
    await _create_session(client, token, agent_id, "Saturday dinner")

    listed = await client.get("/api/v1/sessions", headers={"Authorization": f"Bearer {token}"})

    assert listed.json()[0]["last_message_preview"] is None


@pytest.mark.asyncio
async def test_each_row_gets_its_own_preview_and_agent(client: httpx.AsyncClient):
    """GIVEN two sessions on different agents THEN neither row borrows the other's fields."""
    token, coordinator_id = await _member_and_agent(client)
    researcher = await client.get(
        "/api/v1/agents/researcher", headers={"Authorization": f"Bearer {token}"}
    )
    researcher_id = researcher.json()["id"]

    dinner = await _create_session(client, token, coordinator_id, "Saturday dinner")
    await _say(client, token, dinner, "Dinner at eight")

    papers = await _create_session(client, token, researcher_id, "Jury panel references")
    await _say(client, token, papers, "Three papers on adaptive reuse")

    listed = await client.get("/api/v1/sessions", headers={"Authorization": f"Bearer {token}"})

    rows = {row["title"]: row for row in listed.json()}
    assert rows["Saturday dinner"]["last_message_preview"] == "Dinner at eight"
    assert rows["Saturday dinner"]["agent_avatar"] == "🏡"
    assert rows["Jury panel references"]["last_message_preview"] == "Three papers on adaptive reuse"
    assert rows["Jury panel references"]["agent_avatar"] == "📚"


@pytest.mark.asyncio
async def test_messages_are_indexed_by_the_session_they_belong_to(client: httpx.AsyncClient):
    """
    GIVEN the schema THEN chat_messages is indexed on (session_id, created_at).

    Without it the preview subquery scans the whole message table once per row, and every
    conversation that opens does the same. The index is what makes both a seek.
    """
    from sqlalchemy import inspect

    from app.core.database import Base

    indexes = Base.metadata.tables["chat_messages"].indexes
    columns = {tuple(column.name for column in index.columns) for index in indexes}

    assert ("session_id", "created_at") in columns
