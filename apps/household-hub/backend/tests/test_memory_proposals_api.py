"""Changes to check over HTTP (memory spec §6.1, §8): list, answer, undo, and a chat's look-over."""

from datetime import datetime, timezone

import httpx
import pytest
from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.data.models.memory_model import MemoryModel
from app.data.models.memory_proposal_model import MemoryProposalModel
from app.data.models.session_model import SessionModel
from tests.test_memories import setup_environment
from tests.test_memory_page_api import _memory

LOOKED_OVER = datetime(2026, 10, 16, 10, 52, tzinfo=timezone.utc)


async def _world(client: httpx.AsyncClient, db: AsyncSession):
    """The member said the bins memory; the admin's chat read a change to it. The member's own chat
    remembered one thing and read a change to the admin's boiler memory, which waits for the admin."""
    admin_token, member_token, agent_id, member_chat = await setup_environment(client)
    admin = {"Authorization": f"Bearer {admin_token}"}
    member = {"Authorization": f"Bearer {member_token}"}
    admin_chat = (
        await client.post("/api/v1/sessions", json={"agent_id": agent_id, "title": "House jobs"}, headers=admin)
    ).json()["id"]
    bins = await _memory(client, member_token, agent_id, member_chat, "Bins go out on Tuesday.", scope="household")
    dairy = await _memory(client, member_token, agent_id, member_chat, "Is off dairy this month.")
    boiler = await _memory(client, admin_token, agent_id, admin_chat, "Boiler in October.", scope="household")
    member_id = (await db.execute(select(MemoryModel.user_id).where(MemoryModel.id == bins))).scalar_one()
    admin_id = (await db.execute(select(MemoryModel.user_id).where(MemoryModel.id == boiler))).scalar_one()

    db.add(MemoryProposalModel(
        id="p-bins", memory_id=bins, content="Bins go out on Wednesday.", relation="changed",
        answerer_id=member_id, source_session_id=admin_chat, source_user_id=admin_id, created_at=LOOKED_OVER,
    ))
    db.add(MemoryProposalModel(
        id="p-boiler", memory_id=boiler, content="Boiler moved to November.", relation="changed",
        answerer_id=admin_id, source_session_id=member_chat, source_user_id=member_id, created_at=LOOKED_OVER,
    ))
    messages = (
        await client.post(
            f"/api/v1/sessions/{member_chat}/messages", json={"role": "user", "content": "Hi"}, headers=member
        )
    ).json()
    await db.execute(
        update(SessionModel)
        .where(SessionModel.id == member_chat)
        .values(
            reflected_through_id=messages["id"],
            reflected_at=LOOKED_OVER,
            last_reflection={"added": [dairy], "proposed": ["p-boiler"], "retired": [], "at": LOOKED_OVER.isoformat()},
        )
    )
    await db.commit()
    return admin, member, member_chat, bins, boiler, dairy, messages["id"]


@pytest.mark.asyncio
async def test_each_member_sees_their_changes_and_the_ones_waiting(client: httpx.AsyncClient, db_session):
    admin, member, _, bins, boiler, _, _ = await _world(client, db_session)

    mine = (await client.get("/api/v1/memories/proposals", headers=member)).json()
    theirs = (await client.get("/api/v1/memories/proposals", headers=admin)).json()

    assert [c["id"] for c in mine["to_check"]] == ["p-bins"]
    card = mine["to_check"][0]
    assert card["content"] == "Bins go out on Wednesday."
    assert card["stored"]["id"] == bins and card["stored"]["content"] == "Bins go out on Tuesday."
    assert card["stored"]["is_mine"] is True and card["stored"]["scope"] == "household"
    assert card["can_answer"] is True
    # From the admin's chat: the member sees whose chat, never its title.
    assert card["source"] == {"title": None, "owner_name": "Admin User", "read_at": card["source"]["read_at"]}
    assert [c["id"] for c in mine["waiting"]] == ["p-boiler"]
    waiting = mine["waiting"][0]
    assert waiting["can_answer"] is False and waiting["answerer_name"] == "Admin User"
    assert waiting["source"]["title"] == "Coffee & Schedules"
    assert [c["id"] for c in theirs["to_check"]] == ["p-boiler"]
    assert theirs["to_check"][0]["stored"]["id"] == boiler

    page = (await client.get("/api/v1/memories/page", headers=member)).json()
    assert page["to_check"] == 1


@pytest.mark.asyncio
async def test_use_new_replaces_and_undo_puts_it_back(client: httpx.AsyncClient, db_session):
    admin, member, _, bins, _, _, _ = await _world(client, db_session)

    answered = await client.post(
        "/api/v1/memories/proposals/p-bins/answer", json={"answer": "use_new"}, headers=member
    )

    assert answered.status_code == 200
    body = answered.json()
    assert body["state"] == "replaced" and body["new_memory_id"]
    household = {m["id"]: m for m in (await client.get("/api/v1/memories/page", headers=member)).json()["household"]}
    assert bins not in household
    assert household[body["new_memory_id"]]["content"] == "Bins go out on Wednesday."
    assert household[body["new_memory_id"]]["speaker_name"] == "Admin User"  # said in the admin's chat
    again = await client.post("/api/v1/memories/proposals/p-bins/answer", json={"answer": "keep_both"}, headers=member)
    assert again.status_code == 409

    undone = await client.post("/api/v1/memories/proposals/p-bins/undo", headers=member)

    assert undone.status_code == 200
    assert undone.json()["state"] == "pending" and undone.json()["new_memory_id"] is None
    household = {m["id"] for m in (await client.get("/api/v1/memories/page", headers=member)).json()["household"]}
    assert bins in household and body["new_memory_id"] not in household
    assert (await client.post("/api/v1/memories/proposals/p-bins/undo", headers=member)).status_code == 409


@pytest.mark.asyncio
async def test_only_the_speaker_answers(client: httpx.AsyncClient, db_session):
    admin, member, *_ = await _world(client, db_session)

    refused = await client.post("/api/v1/memories/proposals/p-bins/answer", json={"answer": "use_new"}, headers=admin)
    unknown = await client.post("/api/v1/memories/proposals/nope/answer", json={"answer": "use_new"}, headers=member)
    invalid = await client.post("/api/v1/memories/proposals/p-bins/answer", json={"answer": "maybe"}, headers=member)

    assert refused.status_code == 403
    assert unknown.status_code == 404
    assert invalid.status_code == 422


@pytest.mark.asyncio
async def test_a_looked_over_chat_carries_its_line_and_sheet(client: httpx.AsyncClient, db_session):
    admin, member, member_chat, _, _, dairy, through = await _world(client, db_session)

    detail = (await client.get(f"/api/v1/sessions/{member_chat}", headers=member)).json()
    sheet = await client.get(f"/api/v1/sessions/{member_chat}/looked-over", headers=member)

    line = detail["looked_over"]
    assert line["through_message_id"] == through
    assert (line["remembered"], line["to_check"]) == (1, 0)
    assert line["at"].startswith("2026-10-16T10:52")
    assert sheet.status_code == 200
    body = sheet.json()
    assert body["to_check"] == []
    assert [c["id"] for c in body["waiting"]] == ["p-boiler"]
    assert [m["id"] for m in body["remembered"]] == [dairy]
    assert (await client.get(f"/api/v1/sessions/{member_chat}/looked-over", headers=admin)).status_code in (403, 404)


@pytest.mark.asyncio
async def test_a_chat_never_looked_over_has_no_line(client: httpx.AsyncClient, db_session):
    admin, member, *_ = await _world(client, db_session)
    chats = (await client.get("/api/v1/sessions", headers=admin)).json()
    house = next(c["id"] for c in chats if c["title"] == "House jobs")

    detail = (await client.get(f"/api/v1/sessions/{house}", headers=admin)).json()

    assert detail["looked_over"] is None
    assert (await client.get(f"/api/v1/sessions/{house}/looked-over", headers=admin)).status_code == 404
