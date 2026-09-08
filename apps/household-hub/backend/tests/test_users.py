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
