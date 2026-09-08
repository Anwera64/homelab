import pytest
import httpx


async def setup_environment(client: httpx.AsyncClient) -> tuple[str, str, str, str]:
    """Helper to setup admin, member, session, and return (admin_token, member_token, agent_id, session_id)."""
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

    # 3. Get assistant agent
    agent_resp = await client.get("/api/v1/agents/assistant", headers={"Authorization": f"Bearer {member_token}"})
    agent_id = agent_resp.json()["id"]

    # 4. Create regular session
    session_resp = await client.post(
        "/api/v1/sessions",
        json={"agent_id": agent_id, "title": "Coffee & Schedules", "is_secret": False},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    session_id = session_resp.json()["id"]

    return admin_token, member_token, agent_id, session_id


@pytest.mark.asyncio
async def test_create_and_list_personal_memory(client: httpx.AsyncClient):
    """Personal memory is stored and retrievable by the user."""
    _, member_token, agent_id, session_id = await setup_environment(client)

    mem_payload = {
        "agent_id": agent_id,
        "scope": "personal",
        "category": "preference",
        "content": "Prefers oat milk in coffee drinks",
        "confidence": 0.95,
        "source_session_id": session_id,
    }
    create_resp = await client.post(
        "/api/v1/memories",
        json=mem_payload,
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert create_resp.status_code == 201
    mem_data = create_resp.json()
    assert mem_data["scope"] == "personal"
    assert mem_data["content"] == "Prefers oat milk in coffee drinks"
    assert mem_data["confidence"] == 0.95

    # List personal memories
    list_resp = await client.get(
        "/api/v1/memories",
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert list_resp.status_code == 200
    mems = list_resp.json()
    assert len(mems) == 1
    assert mems[0]["id"] == mem_data["id"]


@pytest.mark.asyncio
async def test_create_and_list_household_memory(client: httpx.AsyncClient):
    """Household memory is visible to all members via /memories/household."""
    admin_token, member_token, agent_id, session_id = await setup_environment(client)

    # Member creates household memory
    mem_payload = {
        "agent_id": agent_id,
        "scope": "household",
        "category": "milestone",
        "content": "Final architecture studio review is on Friday at 10 AM",
        "confidence": 1.0,
        "source_session_id": session_id,
    }
    create_resp = await client.post(
        "/api/v1/memories",
        json=mem_payload,
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert create_resp.status_code == 201
    mem_id = create_resp.json()["id"]

    # Admin reads household memories
    admin_list = await client.get(
        "/api/v1/memories/household",
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert admin_list.status_code == 200
    household_mems = admin_list.json()
    assert any(m["id"] == mem_id for m in household_mems)


@pytest.mark.asyncio
async def test_zero_leak_personal_memory_isolation(client: httpx.AsyncClient):
    """
    Zero-Leak Rule:
    Personal memories of Member cannot be viewed, edited, or deleted by Admin or other members.
    """
    admin_token, member_token, agent_id, session_id = await setup_environment(client)

    # Member creates personal memory
    mem_resp = await client.post(
        "/api/v1/memories",
        json={
            "agent_id": agent_id,
            "scope": "personal",
            "category": "health",
            "content": "Needs medication reminder at 9 PM",
            "source_session_id": session_id,
        },
        headers={"Authorization": f"Bearer {member_token}"},
    )
    mem_id = mem_resp.json()["id"]

    # Admin attempts to read Member's personal memory -> 403 Forbidden!
    admin_get = await client.get(
        f"/api/v1/memories/{mem_id}",
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert admin_get.status_code == 403
    assert "forbidden" in admin_get.json()["detail"].lower() or "zero-leak" in admin_get.json()["detail"].lower()

    # Admin attempts to delete Member's personal memory -> 403 Forbidden!
    admin_del = await client.delete(
        f"/api/v1/memories/{mem_id}",
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert admin_del.status_code == 403


@pytest.mark.asyncio
async def test_user_can_edit_and_delete_memory(client: httpx.AsyncClient):
    """User has full audit and revocation control over their memories."""
    _, member_token, agent_id, session_id = await setup_environment(client)

    # 1. Create memory
    create_resp = await client.post(
        "/api/v1/memories",
        json={
            "agent_id": agent_id,
            "scope": "personal",
            "category": "preference",
            "content": "Prefers almond milk",
            "source_session_id": session_id,
        },
        headers={"Authorization": f"Bearer {member_token}"},
    )
    mem_id = create_resp.json()["id"]

    # 2. User edits memory (correction)
    update_resp = await client.put(
        f"/api/v1/memories/{mem_id}",
        json={"content": "Prefers oat milk, not almond milk"},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert update_resp.status_code == 200
    assert update_resp.json()["content"] == "Prefers oat milk, not almond milk"

    # 3. User deletes/revokes memory
    del_resp = await client.delete(
        f"/api/v1/memories/{mem_id}",
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert del_resp.status_code == 200

    # 4. Verify memory is gone
    get_resp = await client.get(
        f"/api/v1/memories/{mem_id}",
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert get_resp.status_code == 404


@pytest.mark.asyncio
async def test_secret_session_cannot_create_household_memory(client: httpx.AsyncClient):
    """If a memory originates from a Secret session, household scope must be rejected."""
    _, member_token, agent_id, _ = await setup_environment(client)

    # Create secret session
    secret_session_resp = await client.post(
        "/api/v1/sessions",
        json={"agent_id": agent_id, "title": "Secret Surprise Party", "is_secret": True},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    secret_session_id = secret_session_resp.json()["id"]

    # Attempt to publish memory with scope='household' from secret session -> 400 Bad Request!
    bad_mem_resp = await client.post(
        "/api/v1/memories",
        json={
            "agent_id": agent_id,
            "scope": "household",
            "category": "milestone",
            "content": "Surprise anniversary dinner booked for Saturday",
            "source_session_id": secret_session_id,
        },
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert bad_mem_resp.status_code == 400
    assert "secret" in bad_mem_resp.json()["detail"].lower()


@pytest.mark.asyncio
async def test_household_memory_curation_by_admin(client: httpx.AsyncClient):
    """
    Household-scoped memories can be curated (edited or deleted) by Household Admins
    to prevent shared household knowledge lockout.
    """
    admin_token, member_token, agent_id, session_id = await setup_environment(client)

    # 1. Member creates a household memory
    create_resp = await client.post(
        "/api/v1/memories",
        json={
            "agent_id": agent_id,
            "scope": "household",
            "category": "fact",
            "content": "Trash pickup is Wednesday morning",
            "source_session_id": session_id,
        },
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert create_resp.status_code == 201
    mem_id = create_resp.json()["id"]

    # 2. Admin edits the household memory
    admin_edit = await client.put(
        f"/api/v1/memories/{mem_id}",
        json={"content": "Trash pickup is Wednesday morning before 7:00 AM"},
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert admin_edit.status_code == 200
    assert admin_edit.json()["content"] == "Trash pickup is Wednesday morning before 7:00 AM"

    # 3. Admin deletes the household memory
    admin_del = await client.delete(
        f"/api/v1/memories/{mem_id}",
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert admin_del.status_code == 200


@pytest.mark.asyncio
async def test_create_memory_with_nonexistent_agent_returns_404(client: httpx.AsyncClient):
    """Memory creation must validate agent existence and reject non-existent agents with 404."""
    _, member_token, _, session_id = await setup_environment(client)

    resp = await client.post(
        "/api/v1/memories",
        json={
            "agent_id": "00000000-0000-0000-0000-000000000000",
            "scope": "personal",
            "content": "Fact with missing agent",
            "source_session_id": session_id,
        },
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert resp.status_code == 404
    assert "agent" in resp.json()["detail"].lower()


@pytest.mark.asyncio
async def test_memory_confidence_and_content_validation(client: httpx.AsyncClient):
    """Confidence must be within [0.0, 1.0] and content cannot be empty."""
    _, member_token, agent_id, session_id = await setup_environment(client)

    # 1. Confidence > 1.0
    high_conf = await client.post(
        "/api/v1/memories",
        json={"agent_id": agent_id, "scope": "personal", "content": "Valid", "confidence": 1.5, "source_session_id": session_id},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert high_conf.status_code == 422

    # 2. Confidence < 0.0
    low_conf = await client.post(
        "/api/v1/memories",
        json={"agent_id": agent_id, "scope": "personal", "content": "Valid", "confidence": -0.5, "source_session_id": session_id},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert low_conf.status_code == 422

    # 3. Empty content
    empty_content = await client.post(
        "/api/v1/memories",
        json={"agent_id": agent_id, "scope": "personal", "content": "", "source_session_id": session_id},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert empty_content.status_code == 422


