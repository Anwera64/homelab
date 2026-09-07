from datetime import datetime, timedelta, timezone
import pytest
import httpx


async def setup_users(client: httpx.AsyncClient) -> tuple[str, str]:
    """Helper to setup admin and a regular member, returning (admin_token, member_token)."""
    # 1. Admin
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

    # 2. Member
    await client.post(
        "/api/v1/users",
        json={
            "username": "member",
            "email": "member@homelab.local",
            "password": "Password123!",
            "full_name": "Member User",
        },
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    login_resp = await client.post(
        "/api/v1/auth/login",
        json={"username": "member", "password": "Password123!"},
    )
    member_token = login_resp.json()["access_token"]
    return admin_token, member_token


@pytest.mark.asyncio
async def test_built_in_agents_seeded(client: httpx.AsyncClient):
    """Catalog must contain exactly the 2 built-in models: researcher and assistant."""
    admin_token, _ = await setup_users(client)

    response = await client.get(
        "/api/v1/agents",
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert response.status_code == 200
    agents = response.json()
    slugs = [a["slug"] for a in agents]
    assert "researcher" in slugs
    assert "assistant" in slugs
    assert len(agents) == 2

    # Verify attributes
    researcher = next(a for a in agents if a["slug"] == "researcher")
    assert researcher["is_builtin"] is True
    assert researcher["model_alias"] == "qwen3:14b"
    assert "pdf_reader" in researcher["tool_permissions"]


@pytest.mark.asyncio
async def test_cannot_delete_built_in_model(client: httpx.AsyncClient):
    """Built-in models must reject deletion with 400 Bad Request."""
    admin_token, _ = await setup_users(client)

    # Get researcher
    res = await client.get("/api/v1/agents/researcher", headers={"Authorization": f"Bearer {admin_token}"})
    assert res.status_code == 200
    agent_id = res.json()["id"]

    del_resp = await client.delete(
        f"/api/v1/agents/{agent_id}",
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert del_resp.status_code == 400
    assert "built-in" in del_resp.json()["detail"].lower()


@pytest.mark.asyncio
async def test_custom_agent_creation_and_ownership(client: httpx.AsyncClient):
    """Any member can create a model; only the owner can edit or delete it."""
    admin_token, member_token = await setup_users(client)

    # Member creates custom model
    new_agent_payload = {
        "slug": "paper-synthesizer",
        "name": "Paper Synthesizer",
        "description": "Synthesizes complex architectural and urban design papers.",
        "avatar": "📚",
        "system_prompt": "You are a specialized paper synthesizer.",
        "model_alias": "qwen3:14b",
        "temperature": 0.4,
        "tool_permissions": ["pdf_reader"],
    }
    create_resp = await client.post(
        "/api/v1/agents",
        json=new_agent_payload,
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert create_resp.status_code == 201
    created_agent = create_resp.json()
    agent_id = created_agent["id"]
    assert created_agent["is_builtin"] is False
    assert created_agent["owner_id"] is not None

    # Admin (non-owner) attempts to modify it -> 403 Forbidden!
    admin_edit = await client.put(
        f"/api/v1/agents/{agent_id}",
        json={"name": "Hijacked Name"},
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert admin_edit.status_code == 403
    assert "owner" in admin_edit.json()["detail"].lower()

    # Admin (non-owner) attempts to delete it -> 403 Forbidden!
    admin_del = await client.delete(
        f"/api/v1/agents/{agent_id}",
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert admin_del.status_code == 403

    # Owner edits it -> succeeds
    owner_edit = await client.put(
        f"/api/v1/agents/{agent_id}",
        json={"name": "Updated Synthesizer", "temperature": 0.5},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert owner_edit.status_code == 200
    assert owner_edit.json()["name"] == "Updated Synthesizer"


@pytest.mark.asyncio
async def test_soft_delete_and_7_day_undo_grace_period(client: httpx.AsyncClient):
    """
    Deleted models enter trash for 7 days.
    Owner can restore within 7 days.
    Attempting to restore after 7 days returns 410 Gone.
    """
    _, member_token = await setup_users(client)

    # Create custom agent
    create_resp = await client.post(
        "/api/v1/agents",
        json={
            "slug": "diy-assistant",
            "name": "DIY Assistant",
            "system_prompt": "Help with 3D printing and DIY fixes.",
        },
        headers={"Authorization": f"Bearer {member_token}"},
    )
    agent_id = create_resp.json()["id"]

    # 1. Owner soft-deletes model
    del_resp = await client.delete(
        f"/api/v1/agents/{agent_id}",
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert del_resp.status_code == 200
    assert "grace period" in del_resp.json()["message"].lower()

    # 2. Agent is hidden from active list
    active_list = await client.get("/api/v1/agents", headers={"Authorization": f"Bearer {member_token}"})
    assert all(a["id"] != agent_id for a in active_list.json())

    # 3. Agent is present in Trash
    trash_resp = await client.get("/api/v1/agents/trash", headers={"Authorization": f"Bearer {member_token}"})
    assert trash_resp.status_code == 200
    trash_agents = trash_resp.json()
    trashed = next((a for a in trash_agents if a["id"] == agent_id), None)
    assert trashed is not None
    assert trashed["days_remaining_in_grace_period"] == 7

    # 4. Restore model within grace period
    restore_resp = await client.post(
        f"/api/v1/agents/{agent_id}/restore",
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert restore_resp.status_code == 200
    assert restore_resp.json()["id"] == agent_id

    # 5. Agent is back in active list
    active_again = await client.get("/api/v1/agents", headers={"Authorization": f"Bearer {member_token}"})
    assert any(a["id"] == agent_id for a in active_again.json())


@pytest.mark.asyncio
async def test_restore_after_grace_period_expired(client: httpx.AsyncClient, db_session):
    """Attempting to restore a model deleted > 7 days ago returns 410 Gone."""
    from sqlalchemy import update
    from app.models.agent import AgentPersonality

    _, member_token = await setup_users(client)

    create_resp = await client.post(
        "/api/v1/agents",
        json={
            "slug": "old-agent",
            "name": "Old Agent",
            "system_prompt": "Old bot",
        },
        headers={"Authorization": f"Bearer {member_token}"},
    )
    agent_id = create_resp.json()["id"]

    # Delete agent
    await client.delete(f"/api/v1/agents/{agent_id}", headers={"Authorization": f"Bearer {member_token}"})

    # Age deleted_at to 8 days ago
    eight_days_ago = datetime.now(timezone.utc) - timedelta(days=8)
    await db_session.execute(
        update(AgentPersonality)
        .where(AgentPersonality.id == agent_id)
        .values(deleted_at=eight_days_ago)
    )
    await db_session.commit()

    # Attempt to restore -> 410 Gone
    expired_resp = await client.post(
        f"/api/v1/agents/{agent_id}/restore",
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert expired_resp.status_code == 410
    assert "expired" in expired_resp.json()["detail"].lower()
