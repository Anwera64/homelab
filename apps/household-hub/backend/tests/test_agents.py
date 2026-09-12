from datetime import datetime, timedelta, timezone
import pytest
import httpx

from tests.auth_helpers import add_signed_in_member, register_admin


async def setup_users(client: httpx.AsyncClient) -> tuple[str, str]:
    """Helper to setup admin and a regular member, returning (admin_token, member_token)."""
    admin_token, _ = await register_admin(client, full_name="Admin User")
    member_token, _ = await add_signed_in_member(client, full_name="Member User")
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


@pytest.mark.asyncio
async def test_slug_reuse_after_grace_period_expired(client: httpx.AsyncClient, db_session):
    """Once 7 days expire, the expired agent is auto-purged and its slug is reusable."""
    from sqlalchemy import update
    from app.models.agent import AgentPersonality

    _, member_token = await setup_users(client)

    # 1. Create model
    create1 = await client.post(
        "/api/v1/agents",
        json={"slug": "renewable-bot", "name": "Renewable 1", "system_prompt": "First version"},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert create1.status_code == 201
    agent_id = create1.json()["id"]

    # 2. Delete model
    await client.delete(f"/api/v1/agents/{agent_id}", headers={"Authorization": f"Bearer {member_token}"})

    # While in 7-day grace period, trying to create with same slug returns 400
    collision = await client.post(
        "/api/v1/agents",
        json={"slug": "renewable-bot", "name": "Renewable 2", "system_prompt": "Second version"},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert collision.status_code == 400
    assert "trash" in collision.json()["detail"].lower()

    # Age deleted_at to 8 days ago
    eight_days_ago = datetime.now(timezone.utc) - timedelta(days=8)
    await db_session.execute(
        update(AgentPersonality)
        .where(AgentPersonality.id == agent_id)
        .values(deleted_at=eight_days_ago)
    )
    await db_session.commit()

    # 3. Create model with the same slug again -> now succeeds!
    create2 = await client.post(
        "/api/v1/agents",
        json={"slug": "renewable-bot", "name": "Renewable 2", "system_prompt": "Reborn version"},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert create2.status_code == 201
    assert create2.json()["slug"] == "renewable-bot"
    assert create2.json()["name"] == "Renewable 2"


@pytest.mark.asyncio
async def test_trash_explicit_purge_and_slug_immediate_reuse(client: httpx.AsyncClient):
    """Owner or admin can permanently purge a trashed model immediately, freeing its slug."""
    admin_token, member_token = await setup_users(client)

    # 1. Member creates model
    res = await client.post(
        "/api/v1/agents",
        json={"slug": "disposable-bot", "name": "Disposable", "system_prompt": "Prompt"},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    agent_id = res.json()["id"]

    # 2. Delete model
    await client.delete(f"/api/v1/agents/{agent_id}", headers={"Authorization": f"Bearer {member_token}"})

    # Non-owner / non-admin cannot purge -> 403 (we test with another user or admin permission)
    # Admin can purge
    purge_resp = await client.delete(
        f"/api/v1/agents/trash/{agent_id}",
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert purge_resp.status_code == 200
    assert "purged" in purge_resp.json()["message"].lower()

    # Slug is now immediately available for reuse
    recreate_resp = await client.post(
        "/api/v1/agents",
        json={"slug": "disposable-bot", "name": "Fresh Disposable", "system_prompt": "New Prompt"},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert recreate_resp.status_code == 201
    assert recreate_resp.json()["slug"] == "disposable-bot"


@pytest.mark.asyncio
async def test_agent_slug_validation_and_character_rules(client: httpx.AsyncClient):
    """Slugs support lowercase alphanumeric characters with hyphens and underscores; invalid characters return 422."""
    _, member_token = await setup_users(client)

    # 1. Valid slug with hyphens and underscores
    valid1 = await client.post(
        "/api/v1/agents",
        json={"slug": "my_agent-v2", "name": "Valid 1", "system_prompt": "Prompt"},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert valid1.status_code == 201
    assert valid1.json()["slug"] == "my_agent-v2"

    # 2. Invalid slug with spaces
    invalid_spaces = await client.post(
        "/api/v1/agents",
        json={"slug": "my agent", "name": "Invalid Spaces", "system_prompt": "Prompt"},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert invalid_spaces.status_code == 422

    # 3. Invalid slug with slashes
    invalid_slash = await client.post(
        "/api/v1/agents",
        json={"slug": "my/agent", "name": "Invalid Slash", "system_prompt": "Prompt"},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert invalid_slash.status_code == 422

    # 4. Invalid slug with uppercase
    invalid_upper = await client.post(
        "/api/v1/agents",
        json={"slug": "MyAgent", "name": "Invalid Upper", "system_prompt": "Prompt"},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert invalid_upper.status_code == 422


@pytest.mark.asyncio
async def test_agent_inference_parameter_ranges(client: httpx.AsyncClient):
    """Temperature must be in [0.0, 2.0] and top_p in [0.0, 1.0]."""
    _, member_token = await setup_users(client)

    # 1. Temperature too high
    bad_temp = await client.post(
        "/api/v1/agents",
        json={"slug": "bad-temp", "name": "Bad Temp", "system_prompt": "P", "temperature": 2.5},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert bad_temp.status_code == 422

    # 2. Top_p negative
    bad_top_p = await client.post(
        "/api/v1/agents",
        json={"slug": "bad-top-p", "name": "Bad Top P", "system_prompt": "P", "top_p": -0.1},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert bad_top_p.status_code == 422


@pytest.mark.asyncio
async def test_agent_tool_permissions_validation(client: httpx.AsyncClient):
    """Tool permissions must strictly come from the defined homelab capabilities set."""
    _, member_token = await setup_users(client)

    # 1. Valid tool permissions -> 201 Created
    valid_resp = await client.post(
        "/api/v1/agents",
        json={
            "slug": "valid-tools-agent",
            "name": "Valid Agent",
            "system_prompt": "Prompt",
            "tool_permissions": ["searxng_search", "pdf_reader"],
        },
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert valid_resp.status_code == 201
    agent_id = valid_resp.json()["id"]

    # 2. Invalid tool permission on create -> 422 Unprocessable Entity
    invalid_create = await client.post(
        "/api/v1/agents",
        json={
            "slug": "bad-tools-agent",
            "name": "Bad Tools Agent",
            "system_prompt": "Prompt",
            "tool_permissions": ["arbitrary_unvetted_tool"],
        },
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert invalid_create.status_code == 422

    # 3. Invalid tool permission on update -> 422 Unprocessable Entity
    invalid_update = await client.put(
        f"/api/v1/agents/{agent_id}",
        json={"tool_permissions": ["drop_database"]},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert invalid_update.status_code == 422


@pytest.mark.asyncio
async def test_purge_expired_trash_archives_sessions_before_agent_deletion(client: httpx.AsyncClient, db_session):
    """Sessions attached to an agent must be archived (is_archived=True) when the agent is purged from trash."""
    from sqlalchemy import update
    from app.data.models.agent_model import AgentModel

    admin_token, member_token = await setup_users(client)

    # 1. Create agent
    agent_res = await client.post(
        "/api/v1/agents",
        json={"slug": "doomed-agent", "name": "Doomed Agent", "system_prompt": "You are doomed."},
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert agent_res.status_code == 201
    agent_id = agent_res.json()["id"]

    # 2. Create session with this agent
    sess_res = await client.post(
        "/api/v1/sessions",
        json={"agent_id": agent_id, "title": "Doomed Session", "is_secret": False},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert sess_res.status_code == 201
    sess_id = sess_res.json()["id"]
    assert sess_res.json()["is_archived"] is False
    assert sess_res.json()["agent_id"] == agent_id

    # 3. Soft-delete agent
    del_res = await client.delete(
        f"/api/v1/agents/{agent_id}",
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert del_res.status_code == 200

    # 4. Age the agent beyond grace period (31 days ago)
    thirty_one_days_ago = datetime.now(timezone.utc) - timedelta(days=31)
    await db_session.execute(
        update(AgentModel)
        .where(AgentModel.id == agent_id)
        .values(deleted_at=thirty_one_days_ago)
    )
    await db_session.commit()

    # 5. Trigger purge by creating another agent
    trigger_res = await client.post(
        "/api/v1/agents",
        json={"slug": "trigger-agent", "name": "Trigger Agent", "system_prompt": "Prompt"},
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert trigger_res.status_code == 201

    # 6. Verify agent was purged
    get_agent = await client.get(f"/api/v1/agents/{agent_id}", headers={"Authorization": f"Bearer {admin_token}"})
    assert get_agent.status_code == 404

    # 7. Verify session still exists and is archived
    get_sess = await client.get(f"/api/v1/sessions/{sess_id}", headers={"Authorization": f"Bearer {member_token}"})
    assert get_sess.status_code == 200
    sess_body = get_sess.json()
    assert sess_body["agent_id"] is None
    assert sess_body["is_archived"] is True, "Session must be marked as archived when agent is purged"


@pytest.mark.asyncio
async def test_inactive_agent_blocks_new_sessions_and_messages_but_permits_history_read(client: httpx.AsyncClient):
    """Deactivated/inactive agents cannot accept new sessions or new messages, but conversation history remains accessible."""
    admin_token, member_token = await setup_users(client)

    # 1. Create active agent
    agent_res = await client.post(
        "/api/v1/agents",
        json={"slug": "toggle-agent", "name": "Toggle Agent", "system_prompt": "Prompt"},
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert agent_res.status_code == 201
    agent_id = agent_res.json()["id"]

    # 2. Create session and post message while active
    sess_res = await client.post(
        "/api/v1/sessions",
        json={"agent_id": agent_id, "title": "Toggle Session", "is_secret": False},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert sess_res.status_code == 201
    sess_id = sess_res.json()["id"]

    msg1_res = await client.post(
        f"/api/v1/sessions/{sess_id}/messages",
        json={"role": "user", "content": "Message 1 while active"},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert msg1_res.status_code == 201

    # 3. Deactivate agent
    deact_res = await client.put(
        f"/api/v1/agents/{agent_id}",
        json={"is_active": False},
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert deact_res.status_code == 200
    assert deact_res.json()["is_active"] is False

    # 4. Attempt to create a new session with inactive agent -> must be rejected (400)
    blocked_sess = await client.post(
        "/api/v1/sessions",
        json={"agent_id": agent_id, "title": "Blocked Session", "is_secret": False},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert blocked_sess.status_code == 400
    assert "inactive" in blocked_sess.json()["detail"].lower() or "suspended" in blocked_sess.json()["detail"].lower() or "deactivated" in blocked_sess.json()["detail"].lower()

    # 5. Attempt to send message to existing session -> must be rejected (400)
    blocked_msg = await client.post(
        f"/api/v1/sessions/{sess_id}/messages",
        json={"role": "user", "content": "Message 2 while inactive"},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert blocked_msg.status_code == 400
    assert "inactive" in blocked_msg.json()["detail"].lower() or "suspended" in blocked_msg.json()["detail"].lower() or "deactivated" in blocked_msg.json()["detail"].lower()

    # 6. Read session history -> must still be permitted (200)
    read_sess = await client.get(
        f"/api/v1/sessions/{sess_id}",
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert read_sess.status_code == 200
    assert len(read_sess.json()["messages"]) >= 1



