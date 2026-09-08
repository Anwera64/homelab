import pytest
import httpx


async def setup_environment(client: httpx.AsyncClient) -> tuple[str, str, str]:
    """Helper to setup admin, member, and return (admin_token, member_token, agent_id)."""
    admin_reg = await client.post(
        "/api/v1/auth/register-initial",
        json={
            "username": "admin",
            "email": "admin@homelab.local",
            "password": "Password123!",
            "full_name": "Admin",
        },
    )
    admin_token = admin_reg.json()["access_token"]

    # Create member
    await client.post(
        "/api/v1/users",
        json={
            "username": "member",
            "email": "member@homelab.local",
            "password": "Password123!",
            "full_name": "Member",
        },
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    login_resp = await client.post(
        "/api/v1/auth/login",
        json={"username": "member", "password": "Password123!"},
    )
    member_token = login_resp.json()["access_token"]

    # Get assistant agent
    agent_resp = await client.get("/api/v1/agents/assistant", headers={"Authorization": f"Bearer {member_token}"})
    agent_id = agent_resp.json()["id"]

    return admin_token, member_token, agent_id


@pytest.mark.asyncio
async def test_session_lifecycle_and_secret_mode_toggle(client: httpx.AsyncClient):
    """
    Test creating a session, appending messages, toggling Secret Mode on/off,
    and verifying persistence of the is_secret flag.
    """
    _, member_token, agent_id = await setup_environment(client)

    # 1. Create a regular session (is_secret=False)
    session_payload = {
        "agent_id": agent_id,
        "title": "Kitchen Planning",
        "is_secret": False,
    }
    create_resp = await client.post(
        "/api/v1/sessions",
        json=session_payload,
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert create_resp.status_code == 201
    session_data = create_resp.json()
    session_id = session_data["id"]
    assert session_data["is_secret"] is False
    assert session_data["agent_id"] == agent_id

    # 2. Add message to session
    msg_resp = await client.post(
        f"/api/v1/sessions/{session_id}/messages",
        json={"role": "user", "content": "What should we cook for dinner tonight?"},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert msg_resp.status_code == 201
    assert msg_resp.json()["content"] == "What should we cook for dinner tonight?"

    # 3. Toggle Secret Mode ON (Zero-Leak Gossip isolation)
    toggle_resp = await client.patch(
        f"/api/v1/sessions/{session_id}/secret",
        json={"is_secret": True},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert toggle_resp.status_code == 200
    assert toggle_resp.json()["is_secret"] is True

    # 4. Fetch session details -> verify is_secret is True and messages are present
    detail_resp = await client.get(
        f"/api/v1/sessions/{session_id}",
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert detail_resp.status_code == 200
    detail_data = detail_resp.json()
    assert detail_data["is_secret"] is True
    assert len(detail_data["messages"]) == 1


@pytest.mark.asyncio
async def test_session_zero_leak_privacy_boundary(client: httpx.AsyncClient):
    """
    Zero-Leak Rule:
    A user's conversation sessions and message histories are strictly private.
    Another member (and even the Admin) attempting to read another user's session
    must be rejected with 403 Forbidden.
    """
    admin_token, member_token, agent_id = await setup_environment(client)

    # Member creates a secret session (e.g. planning a birthday surprise)
    create_resp = await client.post(
        "/api/v1/sessions",
        json={
            "agent_id": agent_id,
            "title": "Surprise Birthday Gift Planning",
            "is_secret": True,
        },
        headers={"Authorization": f"Bearer {member_token}"},
    )
    session_id = create_resp.json()["id"]

    # Admin attempts to read Member's secret session -> 403 Forbidden!
    admin_snoop = await client.get(
        f"/api/v1/sessions/{session_id}",
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert admin_snoop.status_code == 403
    assert "forbidden" in admin_snoop.json()["detail"].lower() or "zero-leak" in admin_snoop.json()["detail"].lower()

    # Admin attempts to delete Member's session -> 403 Forbidden!
    admin_del = await client.delete(
        f"/api/v1/sessions/{session_id}",
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert admin_del.status_code == 403

    # Member deletes own session -> succeeds
    member_del = await client.delete(
        f"/api/v1/sessions/{session_id}",
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert member_del.status_code == 200


@pytest.mark.asyncio
async def test_session_updated_at_bump_and_message_ordering(client: httpx.AsyncClient):
    """
    Verify that appending a message updates the session's updated_at timestamp,
    bumping it to the top of list_user_sessions, and preserves message order.
    """
    import asyncio
    _, member_token, agent_id = await setup_environment(client)

    # 1. Create two sessions
    sess1_resp = await client.post(
        "/api/v1/sessions",
        json={"agent_id": agent_id, "title": "Session 1"},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    sess1_id = sess1_resp.json()["id"]

    sess2_resp = await client.post(
        "/api/v1/sessions",
        json={"agent_id": agent_id, "title": "Session 2"},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    sess2_id = sess2_resp.json()["id"]

    # Initially, Session 2 is most recently updated
    list_resp = await client.get("/api/v1/sessions", headers={"Authorization": f"Bearer {member_token}"})
    assert list_resp.status_code == 200
    sessions = list_resp.json()
    assert sessions[0]["id"] == sess2_id

    # Add a small delay to ensure distinct timestamps
    await asyncio.sleep(0.01)

    # 2. Append message to Session 1
    msg1_resp = await client.post(
        f"/api/v1/sessions/{sess1_id}/messages",
        json={"role": "user", "content": "Message 1 in Session 1"},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert msg1_resp.status_code == 201

    await asyncio.sleep(0.01)

    # Append second message to Session 1
    msg2_resp = await client.post(
        f"/api/v1/sessions/{sess1_id}/messages",
        json={"role": "assistant", "content": "Message 2 in Session 1"},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert msg2_resp.status_code == 201

    # Session 1 should now be at the TOP of list_user_sessions
    list_bumped = await client.get("/api/v1/sessions", headers={"Authorization": f"Bearer {member_token}"})
    assert list_bumped.status_code == 200
    bumped_sessions = list_bumped.json()
    assert bumped_sessions[0]["id"] == sess1_id

    # Verify messages in Session 1 are in ascending order
    detail_resp = await client.get(f"/api/v1/sessions/{sess1_id}", headers={"Authorization": f"Bearer {member_token}"})
    assert detail_resp.status_code == 200
    detail_messages = detail_resp.json()["messages"]
    assert len(detail_messages) == 2
    assert detail_messages[0]["content"] == "Message 1 in Session 1"
    assert detail_messages[1]["content"] == "Message 2 in Session 1"

