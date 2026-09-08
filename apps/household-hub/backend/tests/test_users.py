import pytest
import httpx


@pytest.mark.asyncio
async def test_admin_delete_member_reassigns_agents_and_purges_personal_data(client: httpx.AsyncClient):
    """
    When an admin deletes a member:
    1. Member's authored custom agents are reassigned to the Admin.
    2. Member's personal space and account are removed (Zero-Leak).
    3. Non-admin cannot delete members (403 Forbidden).
    """
    # 1. Setup Admin
    admin_reg = await client.post(
        "/api/v1/auth/register-initial",
        json={"username": "admin", "email": "admin@homelab.local", "password": "Password123!", "full_name": "Admin"},
    )
    admin_token = admin_reg.json()["access_token"]
    admin_id = admin_reg.json()["user"]["id"]

    # 2. Admin creates member
    mem_resp = await client.post(
        "/api/v1/users",
        json={"username": "member1", "email": "member1@homelab.local", "password": "Password123!", "full_name": "Member 1"},
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    member1_id = mem_resp.json()["id"]

    login_resp = await client.post(
        "/api/v1/auth/login",
        json={"username": "member1", "password": "Password123!"},
    )
    member1_token = login_resp.json()["access_token"]

    # 3. Member creates a custom agent
    agent_resp = await client.post(
        "/api/v1/agents",
        json={"slug": "member_bot", "name": "Member Bot", "system_prompt": "I am a bot"},
        headers={"Authorization": f"Bearer {member1_token}"},
    )
    agent_id = agent_resp.json()["id"]
    assert agent_resp.json()["owner_id"] == member1_id

    # 4. Non-admin attempts to delete -> 403 Forbidden
    forbidden_del = await client.delete(
        f"/api/v1/users/{member1_id}",
        headers={"Authorization": f"Bearer {member1_token}"},
    )
    assert forbidden_del.status_code == 403

    # 5. Admin deletes member
    del_resp = await client.delete(
        f"/api/v1/users/{member1_id}",
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert del_resp.status_code == 200
    assert "deleted" in del_resp.json()["message"].lower()

    # Verify member is gone
    get_mem = await client.get(f"/api/v1/users/{member1_id}", headers={"Authorization": f"Bearer {admin_token}"})
    assert get_mem.status_code == 404

    # Verify custom agent's owner was reassigned to the Admin!
    get_agent = await client.get(f"/api/v1/agents/{agent_id}", headers={"Authorization": f"Bearer {admin_token}"})
    assert get_agent.status_code == 200
    assert get_agent.json()["owner_id"] == admin_id


@pytest.mark.asyncio
async def test_cannot_delete_sole_admin(client: httpx.AsyncClient):
    """The hub must refuse to delete the only administrator account."""
    admin_reg = await client.post(
        "/api/v1/auth/register-initial",
        json={"username": "admin", "email": "admin@homelab.local", "password": "Password123!", "full_name": "Admin"},
    )
    admin_token = admin_reg.json()["access_token"]
    admin_id = admin_reg.json()["user"]["id"]

    # Attempt to delete the only admin -> 400 Bad Request
    del_resp = await client.delete(
        f"/api/v1/users/{admin_id}",
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert del_resp.status_code == 400
    assert "administrator" in del_resp.json()["detail"].lower()


@pytest.mark.asyncio
async def test_delete_admin_when_secondary_admin_exists(client: httpx.AsyncClient):
    """Deleting an admin is permitted if another admin exists; remaining admin inherits models."""
    admin_reg = await client.post(
        "/api/v1/auth/register-initial",
        json={"username": "admin1", "email": "admin1@homelab.local", "password": "Password123!", "full_name": "Admin 1"},
    )
    admin1_token = admin_reg.json()["access_token"]
    admin1_id = admin_reg.json()["user"]["id"]

    # Admin 1 creates Admin 2
    admin2_resp = await client.post(
        "/api/v1/users",
        json={"username": "admin2", "email": "admin2@homelab.local", "password": "Password123!", "full_name": "Admin 2", "is_admin": True},
        headers={"Authorization": f"Bearer {admin1_token}"},
    )
    admin2_id = admin2_resp.json()["id"]

    # Admin 2 creates an agent
    login2 = await client.post("/api/v1/auth/login", json={"username": "admin2", "password": "Password123!"})
    admin2_token = login2.json()["access_token"]
    agent_resp = await client.post(
        "/api/v1/agents",
        json={"slug": "admin2_bot", "name": "Admin 2 Bot", "system_prompt": "Prompt"},
        headers={"Authorization": f"Bearer {admin2_token}"},
    )
    agent_id = agent_resp.json()["id"]

    # Admin 1 deletes Admin 2
    del_resp = await client.delete(f"/api/v1/users/{admin2_id}", headers={"Authorization": f"Bearer {admin1_token}"})
    assert del_resp.status_code == 200

    # Agent is inherited by Admin 1
    get_agent = await client.get(f"/api/v1/agents/{agent_id}", headers={"Authorization": f"Bearer {admin1_token}"})
    assert get_agent.status_code == 200
    assert get_agent.json()["owner_id"] == admin1_id


@pytest.mark.asyncio
async def test_delete_member_preserves_household_memories_and_purges_personal_memories(client: httpx.AsyncClient):
    """
    When an admin deletes a member:
    1. Member's personal memories are permanently purged (Strict Zero-Leak).
    2. Shared household memories are preserved and reassigned to the Admin.
    """
    # 1. Admin registers
    admin_reg = await client.post(
        "/api/v1/auth/register-initial",
        json={"username": "admin", "email": "admin@homelab.local", "password": "Password123!", "full_name": "Admin"},
    )
    admin_token = admin_reg.json()["access_token"]
    admin_id = admin_reg.json()["user"]["id"]

    # 2. Admin creates member
    await client.post(
        "/api/v1/users",
        json={"username": "member1", "email": "member1@homelab.local", "password": "Password123!", "full_name": "Member 1"},
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    login_resp = await client.post(
        "/api/v1/auth/login",
        json={"username": "member1", "password": "Password123!"},
    )
    member_token = login_resp.json()["access_token"]
    member_id = login_resp.json()["user"]["id"]

    # 3. Member creates a personal memory
    p_resp = await client.post(
        "/api/v1/memories",
        json={"scope": "personal", "content": "My private secret note", "category": "preference"},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert p_resp.status_code == 201
    personal_mem_id = p_resp.json()["id"]

    # 4. Member creates a household memory
    h_resp = await client.post(
        "/api/v1/memories",
        json={"scope": "household", "content": "Household Wi-Fi is Homelab-5G", "category": "fact"},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert h_resp.status_code == 201
    household_mem_id = h_resp.json()["id"]

    # 5. Admin deletes member
    del_resp = await client.delete(f"/api/v1/users/{member_id}", headers={"Authorization": f"Bearer {admin_token}"})
    assert del_resp.status_code == 200

    # 6. Personal memory must be purged (Zero-Leak)
    get_p = await client.get(f"/api/v1/memories/{personal_mem_id}", headers={"Authorization": f"Bearer {admin_token}"})
    assert get_p.status_code == 404

    # 7. Household memory must be preserved and reassigned to Admin
    get_h = await client.get(f"/api/v1/memories/{household_mem_id}", headers={"Authorization": f"Bearer {admin_token}"})
    assert get_h.status_code == 200
    assert get_h.json()["user_id"] == admin_id
    assert get_h.json()["content"] == "Household Wi-Fi is Homelab-5G"


@pytest.mark.asyncio
async def test_user_self_service_profile_and_password_update(client: httpx.AsyncClient):
    """Authenticated user can update profile details and password via PATCH /api/v1/users/me."""
    admin_reg = await client.post(
        "/api/v1/auth/register-initial",
        json={"username": "admin", "email": "admin@homelab.local", "password": "Password123!", "full_name": "Admin"},
    )
    admin_token = admin_reg.json()["access_token"]

    await client.post(
        "/api/v1/users",
        json={"username": "member1", "email": "member1@homelab.local", "password": "Password123!", "full_name": "Member 1"},
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    login_resp = await client.post(
        "/api/v1/auth/login",
        json={"username": "member1", "password": "Password123!"},
    )
    member_token = login_resp.json()["access_token"]

    # 1. Member updates their profile and password
    patch_resp = await client.patch(
        "/api/v1/users/me",
        json={
            "full_name": "Updated Member Name",
            "avatar_color": "#10B981",
            "password": "NewSecretPassword456!",
        },
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert patch_resp.status_code == 200
    updated_data = patch_resp.json()
    assert updated_data["full_name"] == "Updated Member Name"
    assert updated_data["avatar_color"] == "#10B981"

    # 2. Login with old password fails
    old_login = await client.post(
        "/api/v1/auth/login",
        json={"username": "member1", "password": "Password123!"},
    )
    assert old_login.status_code == 401

    # 3. Login with new password succeeds
    new_login = await client.post(
        "/api/v1/auth/login",
        json={"username": "member1", "password": "NewSecretPassword456!"},
    )
    assert new_login.status_code == 200
    assert "access_token" in new_login.json()


@pytest.mark.asyncio
async def test_user_registration_input_validation_bounds(client: httpx.AsyncClient):
    """User registration enforces email formatting, username pattern, and non-empty full_name."""
    # 1. Invalid email (missing domain/at)
    bad_email = await client.post(
        "/api/v1/auth/register-initial",
        json={"username": "admin", "email": "not-an-email", "password": "Password123!", "full_name": "Admin"},
    )
    assert bad_email.status_code == 422

    # 2. Invalid username (spaces/symbols)
    bad_user = await client.post(
        "/api/v1/auth/register-initial",
        json={"username": "admin user", "email": "admin@homelab.local", "password": "Password123!", "full_name": "Admin"},
    )
    assert bad_user.status_code == 422

    # 3. Empty full_name
    bad_name = await client.post(
        "/api/v1/auth/register-initial",
        json={"username": "admin", "email": "admin@homelab.local", "password": "Password123!", "full_name": ""},
    )
    assert bad_name.status_code == 422


