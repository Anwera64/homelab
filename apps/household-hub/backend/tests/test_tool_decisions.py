"""
`POST /sessions/{id}/tools/{tool_call_id}/decision`, and the chat while a card waits on it.

A write that needs asking pauses the turn on the answer it was part of. The phone reads the waiting
card from the conversation, answers it here, and the rest of the turn streams back like any other.
Until every card has an answer the chat takes no new message, and an agent that is suspended or put
in the trash takes its cards with it.
"""

import httpx
import pytest

from app.main import app
from app.presentation.api import deps as pres_deps
from tests.auth_helpers import register_admin
from tests.test_turn_resume import _frames


def _paused_answer(*cards: tuple[str, str]) -> dict:
    """An answer paused on one card per (tool_call_id, status), as the turn saves it."""
    return {
        "role": "assistant",
        "content": "I can add it now.",
        "metadata_json": {
            "parts": [
                {"type": "text", "content": "I can add it now."},
                *[
                    {
                        "type": "proposal",
                        "tool_call_id": call_id,
                        "tool": "calendar_write",
                        "action": "create",
                        "arguments": {"title": "Dinner together"},
                        "status": status,
                    }
                    for call_id, status in cards
                ],
            ],
            "paused_turn": {"messages": [{"role": "assistant", "content": "", "tool_calls": []}], "round": 1},
        },
    }


async def _chat(client: httpx.AsyncClient, agent_slug: str = "assistant", agent_id: str | None = None):
    token, _ = await register_admin(client, full_name="Admin")
    headers = {"Authorization": f"Bearer {token}"}
    if agent_id is None:
        agent_id = (await client.get(f"/api/v1/agents/{agent_slug}", headers=headers)).json()["id"]
    created = await client.post("/api/v1/sessions", json={"agent_id": agent_id, "title": "Dinner"}, headers=headers)
    return headers, created.json()["id"]


async def _pause(client, headers, session_id, *cards):
    await client.post(
        f"/api/v1/sessions/{session_id}/messages",
        json={"role": "user", "content": "Put dinner in the calendar"},
        headers=headers,
    )
    await client.post(f"/api/v1/sessions/{session_id}/messages", json=_paused_answer(*cards), headers=headers)


@pytest.fixture
def recorded():
    """Stands in for the background runner and keeps what it was asked to do."""
    calls: list[dict] = []

    def provider():
        async def _runner(queue, **kwargs):
            calls.append(kwargs)
            try:
                await queue.put({"type": "accepted"})
                await queue.put({"type": "done", "message_id": "m1", "assistant_content": "Done."})
            finally:
                await queue.put(None)

        return _runner

    app.dependency_overrides[pres_deps.get_background_chat_stream_runner] = provider
    yield calls
    app.dependency_overrides.pop(pres_deps.get_background_chat_stream_runner, None)


@pytest.mark.asyncio
async def test_GIVEN_a_waiting_card_WHEN_decided_THEN_the_turn_carries_on_as_a_stream(client, recorded):
    headers, session_id = await _chat(client)
    await _pause(client, headers, session_id, ("c1", "pending"))

    res = await client.post(
        f"/api/v1/sessions/{session_id}/tools/c1/decision",
        json={"approved": True, "modified_arguments": {"start_time": "2026-10-03T21:00:00"}},
        headers=headers,
    )

    assert res.status_code == 200
    assert res.headers["content-type"].startswith("text/event-stream")
    assert [data for _, data in _frames(res.text)][-1] == "[DONE]"
    [call] = recorded
    assert call["decision"] == {
        "tool_call_id": "c1",
        "approved": True,
        "modified_arguments": {"start_time": "2026-10-03T21:00:00"},
    }
    # Reflection after the turn is about the question the answer paused in.
    assert call["content"] == "Put dinner in the calendar"


@pytest.mark.asyncio
async def test_GIVEN_no_such_card_WHEN_decided_THEN_404(client, recorded):
    headers, session_id = await _chat(client)
    await _pause(client, headers, session_id, ("c1", "pending"))

    res = await client.post(
        f"/api/v1/sessions/{session_id}/tools/nope/decision", json={"approved": True}, headers=headers
    )

    assert res.status_code == 404
    assert recorded == []


@pytest.mark.asyncio
async def test_GIVEN_an_answered_card_WHEN_decided_again_THEN_409(client, recorded):
    headers, session_id = await _chat(client)
    await _pause(client, headers, session_id, ("c1", "approved"), ("c2", "pending"))

    res = await client.post(
        f"/api/v1/sessions/{session_id}/tools/c1/decision", json={"approved": False}, headers=headers
    )

    assert res.status_code == 409
    assert res.json()["code"] == "already_decided"


@pytest.mark.asyncio
async def test_GIVEN_a_waiting_card_WHEN_a_new_message_is_sent_THEN_409_approval_pending(client, recorded):
    headers, session_id = await _chat(client)
    await _pause(client, headers, session_id, ("c1", "pending"))

    streamed = await client.post(
        f"/api/v1/sessions/{session_id}/chat/stream", json={"content": "Also lunch?"}, headers=headers
    )
    plain = await client.post(f"/api/v1/sessions/{session_id}/chat", json={"content": "Also lunch?"}, headers=headers)

    for res in (streamed, plain):
        assert res.status_code == 409
        assert res.json()["code"] == "approval_pending"
    assert recorded == []


@pytest.mark.asyncio
async def test_GIVEN_a_waiting_card_WHEN_the_chat_is_opened_THEN_it_says_so_and_never_shows_the_saved_turn(client):
    headers, session_id = await _chat(client)
    await _pause(client, headers, session_id, ("c1", "pending"))

    detail = (await client.get(f"/api/v1/sessions/{session_id}", headers=headers)).json()

    assert detail["awaiting_approval"] is True
    answer = detail["messages"][-1]
    assert answer["metadata_json"]["parts"][-1]["status"] == "pending"
    assert "paused_turn" not in answer["metadata_json"]


async def _own_agent(client, headers) -> str:
    created = await client.post(
        "/api/v1/agents",
        json={
            "slug": "planner",
            "name": "Planner",
            "description": "Plans the week.",
            "avatar": "P",
            "system_prompt": "You plan.",
            "tool_permissions": ["calendar_write"],
        },
        headers=headers,
    )
    return created.json()["id"]


@pytest.mark.asyncio
@pytest.mark.parametrize("how", ["trash", "suspend"])
async def test_GIVEN_a_waiting_card_WHEN_its_agent_is_trashed_or_suspended_THEN_the_card_is_dropped(client, how):
    token, _ = await register_admin(client, full_name="Admin")
    headers = {"Authorization": f"Bearer {token}"}
    agent_id = await _own_agent(client, headers)
    session_id = (
        await client.post("/api/v1/sessions", json={"agent_id": agent_id, "title": "Week"}, headers=headers)
    ).json()["id"]
    await _pause(client, headers, session_id, ("c1", "pending"))

    if how == "trash":
        res = await client.delete(f"/api/v1/agents/{agent_id}", headers=headers)
    else:
        res = await client.put(f"/api/v1/agents/{agent_id}", json={"is_active": False}, headers=headers)
    assert res.status_code == 200

    detail = (await client.get(f"/api/v1/sessions/{session_id}", headers=headers)).json()
    assert detail["awaiting_approval"] is False
    assert [part["type"] for part in detail["messages"][-1]["metadata_json"]["parts"]] == ["text"]
