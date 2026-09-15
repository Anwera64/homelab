import pytest
import httpx

from tests.auth_helpers import MEMBER_PIN, add_signed_in_member, register_admin, sign_in


@pytest.mark.asyncio
async def test_admin_delete_member_reassigns_agents_and_purges_personal_data(client: httpx.AsyncClient):
    """
    When an admin deletes a member:
    1. Member's authored custom agents are reassigned to the Admin.
    2. Member's personal space and account are removed (Zero-Leak).
    3. Non-admin cannot delete members (403 Forbidden).
    """
    # 1. Setup Admin and a member
    admin_token, admin_id = await register_admin(client)
    member1_token, member1_id = await add_signed_in_member(client, admin_token, full_name="Member 1")

    # 2. Member creates a custom agent
    agent_resp = await client.post(
        "/api/v1/agents",
        json={"slug": "member_bot", "name": "Member Bot", "system_prompt": "I am a bot"},
        headers={"Authorization": f"Bearer {member1_token}"},
    )
    agent_id = agent_resp.json()["id"]
    assert agent_resp.json()["owner_id"] == member1_id

    # 3. Non-admin attempts to delete -> 403 Forbidden
    forbidden_del = await client.delete(
        f"/api/v1/users/{member1_id}",
        headers={"Authorization": f"Bearer {member1_token}"},
    )
    assert forbidden_del.status_code == 403

    # 4. Admin deletes member
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
    admin_token, admin_id = await register_admin(client)

    # Attempt to delete the only admin -> 409 Conflict
    del_resp = await client.delete(
        f"/api/v1/users/{admin_id}",
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert del_resp.status_code == 409
    assert "administrator" in del_resp.json()["detail"].lower()
    assert del_resp.json()["code"] == "sole_admin"


@pytest.mark.asyncio
async def test_delete_admin_when_secondary_admin_exists(client: httpx.AsyncClient):
    """Deleting an admin is permitted if another admin exists; remaining admin inherits models."""
    admin1_token, admin1_id = await register_admin(client, full_name="Admin 1")
    admin2_token, admin2_id = await add_signed_in_member(client, admin1_token, full_name="Admin 2", is_admin=True)

    # Admin 2 creates an agent
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
    # 1. Admin registers, member joins
    admin_token, admin_id = await register_admin(client)
    member_token, member_id = await add_signed_in_member(client, admin_token, full_name="Member 1")

    # 2. Member creates a personal memory
    p_resp = await client.post(
        "/api/v1/memories",
        json={"scope": "personal", "content": "My private secret note", "category": "preference"},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert p_resp.status_code == 201
    personal_mem_id = p_resp.json()["id"]

    # 3. Member creates a household memory
    h_resp = await client.post(
        "/api/v1/memories",
        json={"scope": "household", "content": "Household Wi-Fi is Homelab-5G", "category": "fact"},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert h_resp.status_code == 201
    household_mem_id = h_resp.json()["id"]

    # 4. Admin deletes member
    del_resp = await client.delete(f"/api/v1/users/{member_id}", headers={"Authorization": f"Bearer {admin_token}"})
    assert del_resp.status_code == 200

    # 5. Personal memory must be purged (Zero-Leak)
    get_p = await client.get(f"/api/v1/memories/{personal_mem_id}", headers={"Authorization": f"Bearer {admin_token}"})
    assert get_p.status_code == 404

    # 6. Household memory must be preserved and reassigned to Admin
    get_h = await client.get(f"/api/v1/memories/{household_mem_id}", headers={"Authorization": f"Bearer {admin_token}"})
    assert get_h.status_code == 200
    assert get_h.json()["user_id"] == admin_id
    assert get_h.json()["content"] == "Household Wi-Fi is Homelab-5G"


@pytest.mark.asyncio
async def test_user_self_service_profile_update(client: httpx.AsyncClient):
    """A member changes their own name and colour via PATCH /api/v1/users/me — never their PIN."""
    admin_token, _ = await register_admin(client)
    member_token, member_id = await add_signed_in_member(client, admin_token, full_name="Member 1")

    patch_resp = await client.patch(
        "/api/v1/users/me",
        json={"full_name": "Updated Member Name", "avatar_color": "#C05638", "pin": "999999"},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert patch_resp.status_code == 200
    updated_data = patch_resp.json()
    assert updated_data["full_name"] == "Updated Member Name"
    assert updated_data["avatar_color"] == "#C05638"

    # The PIN in the body was ignored: the old one still signs in
    await sign_in(client, member_id, MEMBER_PIN)
