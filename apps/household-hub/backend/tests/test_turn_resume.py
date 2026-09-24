"""
A phone that lost the stream mid-answer comes back and asks for the rest.

Locking the phone kills the socket; the hub keeps writing. Every event a turn sends carries an id
of the form `<turn>:<n>`, and `GET /chat/stream?last_event_id=<turn>:<n>` hands back what came
after it — live if the turn is still going. A turn the hub no longer holds is 410 Gone, which tells
the phone to read the saved message instead.
"""

import asyncio
import json

import httpx
import pytest

from app.main import app
from app.presentation.api import deps as pres_deps
from tests.auth_helpers import add_signed_in_member, register_admin


def _frames(body: str) -> list[tuple[str | None, str]]:
    """(id, data) for every SSE frame in [body]."""
    frames = []
    for block in body.split("\n\n"):
        event_id, data = None, None
        for line in block.splitlines():
            if line.startswith("id:"):
                event_id = line[len("id:"):].strip()
            elif line.startswith("data:"):
                data = line[len("data:"):].strip()
        if data is not None:
            frames.append((event_id, data))
    return frames


def _scripted_runner(events: list[dict], hold: asyncio.Event | None = None, hold_after: int = 0):
    """A stand-in for the background runner: says [events], pausing on [hold] after [hold_after]."""

    def provider():
        async def _runner(queue, **kwargs):
            try:
                for index, event in enumerate(events):
                    if hold is not None and index == hold_after:
                        await hold.wait()
                    await queue.put(event)
            finally:
                await queue.put(None)

        return _runner

    return provider


async def _session(client: httpx.AsyncClient) -> tuple[str, str]:
    token, _ = await register_admin(client, full_name="Admin")
    agent = await client.get("/api/v1/agents/assistant", headers={"Authorization": f"Bearer {token}"})
    created = await client.post(
        "/api/v1/sessions",
        json={"agent_id": agent.json()["id"], "title": "Saturday dinner"},
        headers={"Authorization": f"Bearer {token}"},
    )
    return token, created.json()["id"]


SCRIPT = [
    {"type": "accepted"},
    {"type": "delta", "content": "Hel"},
    {"type": "delta", "content": "lo"},
    {"type": "done", "message_id": "m1", "assistant_content": "Hello"},
]


@pytest.fixture
def scripted():
    def install(events=SCRIPT, **kwargs):
        app.dependency_overrides[pres_deps.get_background_chat_stream_runner] = _scripted_runner(events, **kwargs)

    yield install
    app.dependency_overrides.pop(pres_deps.get_background_chat_stream_runner, None)


async def _send(client, token, session_id) -> httpx.Response:
    return await client.post(
        f"/api/v1/sessions/{session_id}/chat/stream",
        json={"content": "Say hello"},
        headers={"Authorization": f"Bearer {token}"},
    )


@pytest.mark.asyncio
async def test_every_streamed_event_carries_its_turn_and_number(client: httpx.AsyncClient, scripted):
    """GIVEN a turn WHEN it is streamed THEN each event has id <turn>:<n>, n counting up from 1."""
    scripted()
    token, session_id = await _session(client)

    response = await _send(client, token, session_id)
    frames = _frames(response.text)

    ids = [event_id for event_id, data in frames if data != "[DONE]"]
    turns = {event_id.split(":")[0] for event_id in ids}
    assert len(turns) == 1
    assert [int(event_id.split(":")[1]) for event_id in ids] == [1, 2, 3, 4]
    assert frames[-1][1] == "[DONE]"


@pytest.mark.asyncio
async def test_resuming_a_finished_turn_returns_only_what_came_after(client: httpx.AsyncClient, scripted):
    """GIVEN a finished turn WHEN resumed after event 2 THEN events 3 and 4 arrive, then [DONE]."""
    scripted()
    token, session_id = await _session(client)
    first = _frames((await _send(client, token, session_id)).text)
    turn = first[0][0].split(":")[0]

    resumed = await client.get(
        f"/api/v1/sessions/{session_id}/chat/stream",
        params={"last_event_id": f"{turn}:2"},
        headers={"Authorization": f"Bearer {token}"},
    )

    assert resumed.status_code == 200
    frames = _frames(resumed.text)
    assert [event_id for event_id, _ in frames[:-1]] == [f"{turn}:3", f"{turn}:4"]
    assert json.loads(frames[0][1]) == {"type": "delta", "content": "lo"}
    assert frames[-1][1] == "[DONE]"


@pytest.mark.asyncio
async def test_the_last_event_id_header_works_like_the_query(client: httpx.AsyncClient, scripted):
    """GIVEN a finished turn WHEN resumed with the standard Last-Event-ID header THEN it resumes the same."""
    scripted()
    token, session_id = await _session(client)
    turn = _frames((await _send(client, token, session_id)).text)[0][0].split(":")[0]

    resumed = await client.get(
        f"/api/v1/sessions/{session_id}/chat/stream",
        headers={"Authorization": f"Bearer {token}", "Last-Event-ID": f"{turn}:3"},
    )

    assert [event_id for event_id, _ in _frames(resumed.text)[:-1]] == [f"{turn}:4"]


@pytest.mark.asyncio
async def test_a_turn_the_hub_no_longer_holds_is_gone(client: httpx.AsyncClient, scripted):
    """GIVEN an id from some other turn WHEN resumed THEN 410, so the phone reads the saved message."""
    scripted()
    token, session_id = await _session(client)
    await _send(client, token, session_id)

    resumed = await client.get(
        f"/api/v1/sessions/{session_id}/chat/stream",
        params={"last_event_id": "not-this-turn:2"},
        headers={"Authorization": f"Bearer {token}"},
    )

    assert resumed.status_code == 410


@pytest.mark.asyncio
async def test_a_conversation_that_never_streamed_is_gone(client: httpx.AsyncClient):
    """GIVEN no turn ever streamed WHEN resumed THEN 410."""
    token, session_id = await _session(client)

    resumed = await client.get(
        f"/api/v1/sessions/{session_id}/chat/stream",
        params={"last_event_id": "abc:1"},
        headers={"Authorization": f"Bearer {token}"},
    )

    assert resumed.status_code == 410


@pytest.mark.asyncio
async def test_another_members_turn_cannot_be_resumed(client: httpx.AsyncClient, scripted):
    """GIVEN someone else's turn WHEN a different member resumes it THEN refused like any other read."""
    scripted()
    token, session_id = await _session(client)
    turn = _frames((await _send(client, token, session_id)).text)[0][0].split(":")[0]
    stranger, _ = await add_signed_in_member(client, admin_token=token, full_name="Stranger")

    resumed = await client.get(
        f"/api/v1/sessions/{session_id}/chat/stream",
        params={"last_event_id": f"{turn}:0"},
        headers={"Authorization": f"Bearer {stranger}"},
    )
    read = await client.get(
        f"/api/v1/sessions/{session_id}", headers={"Authorization": f"Bearer {stranger}"}
    )

    assert resumed.status_code == read.status_code
    assert resumed.status_code in (403, 404)


@pytest.mark.asyncio
async def test_resuming_a_running_turn_follows_it_live(client: httpx.AsyncClient, scripted):
    """GIVEN a turn paused after two events WHEN resumed after 2 and the turn goes on THEN the rest arrive."""
    hold = asyncio.Event()
    scripted(hold=hold, hold_after=2)
    token, session_id = await _session(client)
    registry = pres_deps.get_turn_log_registry()

    sending = asyncio.create_task(_send(client, token, session_id))
    for _ in range(100):
        log = registry.get(session_id)
        if log is not None and len(log.events) == 2:
            break
        await asyncio.sleep(0.01)
    turn = registry.get(session_id).turn_id

    resuming = asyncio.create_task(
        client.get(
            f"/api/v1/sessions/{session_id}/chat/stream",
            params={"last_event_id": f"{turn}:2"},
            headers={"Authorization": f"Bearer {token}"},
        )
    )
    await asyncio.sleep(0.05)
    hold.set()

    resumed = await asyncio.wait_for(resuming, timeout=2)
    await asyncio.wait_for(sending, timeout=2)
    assert [event_id for event_id, _ in _frames(resumed.text)[:-1]] == [f"{turn}:3", f"{turn}:4"]
