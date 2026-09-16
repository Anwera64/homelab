"""
Removing someone from the household. They are deactivated, not deleted: their name and colour stay,
so what they shared keeps their name on it, and everything private to them is erased.
"""
import uuid

import pytest
import httpx
from sqlalchemy import text

from tests.auth_helpers import ADMIN_PIN, MEMBER_PIN, add_signed_in_member, register_admin, sign_in
from tests.conftest import TestingSessionLocal


def _bearer(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


async def _rows(table: str, user_column: str, user_id: str) -> int:
    async with TestingSessionLocal() as session:
        res = await session.execute(
            text(f"SELECT COUNT(*) FROM {table} WHERE {user_column} = :user_id"), {"user_id": user_id}
        )
        return res.scalar() or 0


async def _give_liam_something_of_his_own(client: httpx.AsyncClient, liam_token: str, liam_id: str) -> str:
    """A chat with a message in it, a private memory, a calendar connection and a note. Returns the session id."""
    agent = await client.get("/api/v1/agents/assistant", headers=_bearer(liam_token))
    session_resp = await client.post(
        "/api/v1/sessions",
        json={"agent_id": agent.json()["id"], "title": "Liam's chat"},
        headers=_bearer(liam_token),
    )
    session_id = session_resp.json()["id"]
    await client.post(
        f"/api/v1/sessions/{session_id}/messages",
        json={"role": "user", "content": "Something I said"},
        headers=_bearer(liam_token),
    )
    await client.post(
        "/api/v1/memories",
        json={"scope": "personal", "content": "Liam takes his coffee black", "category": "preference"},
        headers=_bearer(liam_token),
    )
    async with TestingSessionLocal() as session:
        await session.execute(
            text(
                "INSERT INTO calendar_credentials (id, user_id, provider, url, username, encrypted_secret,"
                " calendar_name, is_active, created_at, updated_at)"
                " VALUES (:id, :user_id, 'caldav', 'https://caldav.icloud.com', 'liam@icloud.com', 'encrypted',"
                " 'Default', 1, datetime('now'), datetime('now'))"
            ),
            {"id": str(uuid.uuid4()), "user_id": liam_id},
        )
        await session.execute(
            text(
                "INSERT INTO app_documents (id, user_id, title, content, format, version, created_at, updated_at)"
                " VALUES (:id, :user_id, 'Sourdough', 'Feed it daily', 'markdown', 1, datetime('now'), datetime('now'))"
            ),
            {"id": str(uuid.uuid4()), "user_id": liam_id},
        )
        await session.commit()
    return session_id


async def _household(client: httpx.AsyncClient) -> tuple[str, str, str, str]:
    """Emma the admin and Liam the member. Returns (emma_token, emma_id, liam_token, liam_id)."""
    emma_token, emma_id = await register_admin(client, full_name="Emma")
    liam_token, liam_id = await add_signed_in_member(client, emma_token, full_name="Liam")
    return emma_token, emma_id, liam_token, liam_id


@pytest.mark.asyncio
async def test_removing_a_member_keeps_their_name_and_colour(client: httpx.AsyncClient):
    """Deactivated, not deleted, so the facts they shared keep their real source."""
    emma_token, _, _, liam_id = await _household(client)

    resp = await client.delete(f"/api/v1/users/{liam_id}", headers=_bearer(emma_token))

    assert resp.status_code == 200
    liam = await client.get(f"/api/v1/users/{liam_id}", headers=_bearer(emma_token))
    assert liam.status_code == 200
    assert liam.json()["full_name"] == "Liam"
    assert liam.json()["is_active"] is False


@pytest.mark.asyncio
async def test_a_removed_member_leaves_the_picker_and_cannot_sign_in(client: httpx.AsyncClient):
    emma_token, _, _, liam_id = await _household(client)

    await client.delete(f"/api/v1/users/{liam_id}", headers=_bearer(emma_token))

    picker = await client.get("/api/v1/auth/members")
    assert [m["full_name"] for m in picker.json()] == ["Emma"]
    assert (await client.post("/api/v1/auth/login", json={"user_id": liam_id, "pin": MEMBER_PIN})).status_code == 401


@pytest.mark.asyncio
async def test_a_removed_members_phone_stops_working(client: httpx.AsyncClient):
    emma_token, _, liam_token, liam_id = await _household(client)

    await client.delete(f"/api/v1/users/{liam_id}", headers=_bearer(emma_token))

    assert (await client.get("/api/v1/auth/me", headers=_bearer(liam_token))).status_code == 401


@pytest.mark.asyncio
async def test_removing_erases_their_chats_private_memories_space_calendar_and_notes(client: httpx.AsyncClient):
    emma_token, _, liam_token, liam_id = await _household(client)
    session_id = await _give_liam_something_of_his_own(client, liam_token, liam_id)

    await client.delete(f"/api/v1/users/{liam_id}", headers=_bearer(emma_token))

    assert await _rows("conversation_sessions", "user_id", liam_id) == 0
    assert await _rows("chat_messages", "session_id", session_id) == 0
    assert await _rows("agent_memories", "user_id", liam_id) == 0
    assert await _rows("spaces", "owner_id", liam_id) == 0
    assert await _rows("calendar_credentials", "user_id", liam_id) == 0
    assert await _rows("app_documents", "user_id", liam_id) == 0


@pytest.mark.asyncio
async def test_what_they_shared_keeps_their_name(client: httpx.AsyncClient):
    """The attributed bus exists so nothing is re-attributed to the admin."""
    emma_token, _, liam_token, liam_id = await _household(client)
    shared = await client.post(
        "/api/v1/memories",
        json={"scope": "household", "content": "The boiler is serviced in October", "category": "fact"},
        headers=_bearer(liam_token),
    )
    async with TestingSessionLocal() as session:
        await session.execute(
            text(
                "INSERT INTO gossip_milestones (id, source_user_id, source_username, reporting_agent_name,"
                " target_scope, category, summary, details_json, is_active, created_at, updated_at)"
                " VALUES (:id, :user_id, 'Liam', 'Home Coordinator', 'household', 'milestone',"
                " 'Liam is back by seven', '{}', 1, datetime('now'), datetime('now'))"
            ),
            {"id": str(uuid.uuid4()), "user_id": liam_id},
        )
        await session.commit()

    await client.delete(f"/api/v1/users/{liam_id}", headers=_bearer(emma_token))

    kept = await client.get(f"/api/v1/memories/{shared.json()['id']}", headers=_bearer(emma_token))
    assert kept.status_code == 200
    assert kept.json()["user_id"] == liam_id
    assert await _rows("gossip_milestones", "source_user_id", liam_id) == 1


@pytest.mark.asyncio
async def test_their_agents_pass_to_the_admin(client: httpx.AsyncClient):
    """Ownership, not attribution: an agent needs a living owner to stay editable."""
    emma_token, emma_id, liam_token, liam_id = await _household(client)
    agent = await client.post(
        "/api/v1/agents",
        json={"slug": "liam_bot", "name": "Liam Bot", "system_prompt": "I am a bot"},
        headers=_bearer(liam_token),
    )

    await client.delete(f"/api/v1/users/{liam_id}", headers=_bearer(emma_token))

    passed_on = await client.get(f"/api/v1/agents/{agent.json()['id']}", headers=_bearer(emma_token))
    assert passed_on.json()["owner_id"] == emma_id


@pytest.mark.asyncio
async def test_their_name_is_free_for_someone_new(client: httpx.AsyncClient):
    emma_token, _, _, liam_id = await _household(client)
    await client.delete(f"/api/v1/users/{liam_id}", headers=_bearer(emma_token))

    invite = await client.post("/api/v1/invites", json={"invited_name": "Liam"}, headers=_bearer(emma_token))

    assert invite.status_code == 201


@pytest.mark.asyncio
async def test_a_member_cannot_remove_anyone(client: httpx.AsyncClient):
    emma_token, emma_id, liam_token, _ = await _household(client)

    resp = await client.delete(f"/api/v1/users/{emma_id}", headers=_bearer(liam_token))

    assert resp.status_code == 403


@pytest.mark.asyncio
async def test_the_admin_leaves_rather_than_removing_themselves(client: httpx.AsyncClient):
    emma_token, emma_id, _, _ = await _household(client)

    resp = await client.delete(f"/api/v1/users/{emma_id}", headers=_bearer(emma_token))

    assert resp.status_code == 400


@pytest.mark.asyncio
async def test_the_members_list_shows_active_members_only(client: httpx.AsyncClient):
    emma_token, _, _, liam_id = await _household(client)
    await client.delete(f"/api/v1/users/{liam_id}", headers=_bearer(emma_token))

    members = await client.get("/api/v1/users", headers=_bearer(emma_token))

    assert [m["full_name"] for m in members.json()] == ["Emma"]


@pytest.mark.asyncio
async def test_a_member_changes_their_own_name_and_colour_but_never_their_pin_here(client: httpx.AsyncClient):
    emma_token, _ = await register_admin(client)
    member_token, member_id = await add_signed_in_member(client, emma_token, full_name="Member 1")

    patch_resp = await client.patch(
        "/api/v1/users/me",
        json={"full_name": "Updated Member Name", "avatar_color": "#C05638", "pin": "999999"},
        headers=_bearer(member_token),
    )

    assert patch_resp.status_code == 200
    assert patch_resp.json()["full_name"] == "Updated Member Name"
    assert patch_resp.json()["avatar_color"] == "#C05638"
    await sign_in(client, member_id, MEMBER_PIN)
