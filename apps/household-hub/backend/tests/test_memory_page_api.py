"""The memory page's endpoints (memory spec §8): the list, ✕ and Undo."""

import httpx
import pytest

from tests.test_memories import setup_environment


async def _memory(client, token, agent_id, session_id, content, scope="personal"):
    resp = await client.post(
        "/api/v1/memories",
        json={"agent_id": agent_id, "scope": scope, "content": content, "source_session_id": session_id},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert resp.status_code == 201
    return resp.json()["id"]


@pytest.mark.asyncio
async def test_the_page_shows_yours_and_the_household(client: httpx.AsyncClient):
    """GIVEN a member's personal and household memories and the admin's WHEN the member opens the page THEN each lands on its tab."""
    admin_token, member_token, agent_id, session_id = await setup_environment(client)
    mine = await _memory(client, member_token, agent_id, session_id, "Prefers dinner after 20:00")
    shared = await _memory(client, member_token, agent_id, session_id, "Dinner Saturday", scope="household")
    admin_session = (
        await client.post(
            "/api/v1/sessions",
            json={"agent_id": agent_id, "title": "Admin", "is_secret": False},
            headers={"Authorization": f"Bearer {admin_token}"},
        )
    ).json()["id"]
    await _memory(client, admin_token, agent_id, admin_session, "Admin's private note")
    theirs = await _memory(client, admin_token, agent_id, admin_session, "Boiler in October", scope="household")

    resp = await client.get("/api/v1/memories/page", headers={"Authorization": f"Bearer {member_token}"})

    assert resp.status_code == 200
    body = resp.json()
    assert [m["id"] for m in body["yours"]] == [mine]
    row = body["yours"][0]
    assert row["content"] == "Prefers dinner after 20:00"
    assert row["agent_name"]
    assert row["can_remove"] is True
    assert row["origin"] == "manual"
    assert row["expires_at"] is None
    household = {m["id"]: m for m in body["household"]}
    assert set(household) == {shared, theirs}
    assert household[shared]["is_mine"] is True and household[shared]["can_remove"] is True
    assert household[theirs]["speaker_name"] == "Admin User"
    assert household[theirs]["is_mine"] is False and household[theirs]["can_remove"] is False


@pytest.mark.asyncio
async def test_remove_then_undo(client: httpx.AsyncClient):
    """GIVEN a memory WHEN its member removes it THEN it leaves the page; WHEN they undo THEN it is back."""
    _, member_token, agent_id, session_id = await setup_environment(client)
    headers = {"Authorization": f"Bearer {member_token}"}
    mem_id = await _memory(client, member_token, agent_id, session_id, "Likes tea")

    removed = await client.delete(f"/api/v1/memories/{mem_id}", headers=headers)
    assert removed.status_code == 204
    page = (await client.get("/api/v1/memories/page", headers=headers)).json()
    assert page["yours"] == []
    still_stored = await client.get(f"/api/v1/memories/{mem_id}", headers=headers)
    assert still_stored.status_code == 200
    assert still_stored.json()["is_active"] is False

    restored = await client.post(f"/api/v1/memories/{mem_id}/restore", headers=headers)
    assert restored.status_code == 200
    page = (await client.get("/api/v1/memories/page", headers=headers)).json()
    assert [m["id"] for m in page["yours"]] == [mem_id]


@pytest.mark.asyncio
async def test_an_admin_cannot_remove_or_restore_someone_elses_household_memory(client: httpx.AsyncClient):
    admin_token, member_token, agent_id, session_id = await setup_environment(client)
    mem_id = await _memory(client, member_token, agent_id, session_id, "Trash on Wednesday", scope="household")
    admin = {"Authorization": f"Bearer {admin_token}"}

    assert (await client.delete(f"/api/v1/memories/{mem_id}", headers=admin)).status_code == 403
    await client.delete(f"/api/v1/memories/{mem_id}", headers={"Authorization": f"Bearer {member_token}"})
    assert (await client.post(f"/api/v1/memories/{mem_id}/restore", headers=admin)).status_code == 403


@pytest.mark.asyncio
async def test_removing_or_restoring_an_unknown_memory_is_404(client: httpx.AsyncClient):
    _, member_token, _, _ = await setup_environment(client)
    headers = {"Authorization": f"Bearer {member_token}"}

    assert (await client.delete("/api/v1/memories/nope", headers=headers)).status_code == 404
    assert (await client.post("/api/v1/memories/nope/restore", headers=headers)).status_code == 404
