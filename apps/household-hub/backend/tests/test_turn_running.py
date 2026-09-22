"""
Whether a turn is being generated right now, told to the phone.

A stream that dies mid-answer leaves the phone holding half a reply and no way to know whether the
hub is still writing the rest, has finished, or died trying. The transcript alone cannot say: the
assistant row is only written once generation completes, so "no answer yet" covers all three.

The hub already knows — `SessionLockRegistry` holds a lock for exactly the duration of a turn — it
simply never said. This is that, on the session it belongs to, so the same read that fetches the
answer also reports whether one is still coming.
"""

import pytest
import httpx

from app.presentation.api.deps import get_session_lock_registry
from tests.auth_helpers import register_admin


async def _session(client: httpx.AsyncClient) -> tuple[str, str]:
    token, _ = await register_admin(client, full_name="Admin")
    agent = await client.get("/api/v1/agents/assistant", headers={"Authorization": f"Bearer {token}"})
    created = await client.post(
        "/api/v1/sessions",
        json={"agent_id": agent.json()["id"], "title": "Saturday dinner"},
        headers={"Authorization": f"Bearer {token}"},
    )
    return token, created.json()["id"]


@pytest.mark.asyncio
async def test_an_idle_conversation_reports_no_turn_running(client: httpx.AsyncClient):
    """GIVEN nothing being generated WHEN the session is read THEN turn_running is false."""
    token, session_id = await _session(client)

    read = await client.get(
        f"/api/v1/sessions/{session_id}", headers={"Authorization": f"Bearer {token}"}
    )

    assert read.status_code == 200
    assert read.json()["turn_running"] is False


@pytest.mark.asyncio
async def test_a_conversation_mid_turn_says_so(client: httpx.AsyncClient):
    """GIVEN a turn holding the session lock WHEN the session is read THEN turn_running is true."""
    token, session_id = await _session(client)
    registry = get_session_lock_registry()

    assert await registry.try_acquire(session_id)
    try:
        read = await client.get(
            f"/api/v1/sessions/{session_id}", headers={"Authorization": f"Bearer {token}"}
        )
    finally:
        await registry.release(session_id)

    assert read.json()["turn_running"] is True


@pytest.mark.asyncio
async def test_the_lock_releasing_is_visible_on_the_next_read(client: httpx.AsyncClient):
    """GIVEN a turn that has ended THEN the next read reports it, so polling can stop."""
    token, session_id = await _session(client)
    registry = get_session_lock_registry()

    await registry.try_acquire(session_id)
    await registry.release(session_id)

    read = await client.get(
        f"/api/v1/sessions/{session_id}", headers={"Authorization": f"Bearer {token}"}
    )

    assert read.json()["turn_running"] is False


@pytest.mark.asyncio
async def test_one_conversations_turn_is_not_another_conversations(client: httpx.AsyncClient):
    """GIVEN two sessions THEN a turn on one leaves the other idle."""
    token, busy_id = await _session(client)
    agent = await client.get("/api/v1/agents/assistant", headers={"Authorization": f"Bearer {token}"})
    other = await client.post(
        "/api/v1/sessions",
        json={"agent_id": agent.json()["id"], "title": "Groceries"},
        headers={"Authorization": f"Bearer {token}"},
    )
    idle_id = other.json()["id"]

    registry = get_session_lock_registry()
    assert await registry.try_acquire(busy_id)
    try:
        busy = await client.get(
            f"/api/v1/sessions/{busy_id}", headers={"Authorization": f"Bearer {token}"}
        )
        idle = await client.get(
            f"/api/v1/sessions/{idle_id}", headers={"Authorization": f"Bearer {token}"}
        )
    finally:
        await registry.release(busy_id)

    assert busy.json()["turn_running"] is True
    assert idle.json()["turn_running"] is False


@pytest.mark.asyncio
async def test_regenerating_an_answered_conversation_is_refused(client: httpx.AsyncClient):
    """GIVEN a conversation with nothing outstanding THEN there is nothing to answer again."""
    token, session_id = await _session(client)
    await client.post(
        f"/api/v1/sessions/{session_id}/messages",
        json={"role": "user", "content": "Plan meals"},
        headers={"Authorization": f"Bearer {token}"},
    )
    await client.post(
        f"/api/v1/sessions/{session_id}/messages",
        json={"role": "assistant", "content": "Here it is."},
        headers={"Authorization": f"Bearer {token}"},
    )

    response = await client.post(
        f"/api/v1/sessions/{session_id}/chat/regenerate",
        headers={"Authorization": f"Bearer {token}"},
    )

    assert response.status_code == 409


@pytest.mark.asyncio
async def test_regenerating_an_empty_conversation_is_refused(client: httpx.AsyncClient):
    """GIVEN a conversation nobody has spoken in THEN there is no question to answer."""
    token, session_id = await _session(client)

    response = await client.post(
        f"/api/v1/sessions/{session_id}/chat/regenerate",
        headers={"Authorization": f"Bearer {token}"},
    )

    assert response.status_code == 409


@pytest.mark.asyncio
async def test_regenerating_releases_the_lock_when_it_refuses(client: httpx.AsyncClient):
    """
    GIVEN a refused regenerate THEN the session is not left locked.

    The lock is taken before the transcript is checked, so a refusal that forgot to release it
    would wedge the conversation: every later turn would answer 409 until the hub restarted.
    """
    token, session_id = await _session(client)

    await client.post(
        f"/api/v1/sessions/{session_id}/chat/regenerate",
        headers={"Authorization": f"Bearer {token}"},
    )

    assert get_session_lock_registry().is_locked(session_id) is False


@pytest.mark.asyncio
async def test_regenerating_while_a_turn_runs_is_a_conflict(client: httpx.AsyncClient):
    """GIVEN a turn already running THEN regenerate waits its turn like any other send."""
    token, session_id = await _session(client)
    await client.post(
        f"/api/v1/sessions/{session_id}/messages",
        json={"role": "user", "content": "Plan meals"},
        headers={"Authorization": f"Bearer {token}"},
    )

    registry = get_session_lock_registry()
    assert await registry.try_acquire(session_id)
    try:
        response = await client.post(
            f"/api/v1/sessions/{session_id}/chat/regenerate",
            headers={"Authorization": f"Bearer {token}"},
        )
    finally:
        await registry.release(session_id)

    assert response.status_code == 409
