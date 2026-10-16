"""
A turn always wins (memory spec §2.2): whether any turn is running is known hub-wide, and a turn
starting tells whoever listens, so reflection can be cancelled. The chat locks feed it.
"""

import pytest

from app.bootstrap.turn_activity import HubTurnActivity
from app.presentation.api.session_lock import SessionLockRegistry


@pytest.mark.asyncio
async def test_GIVEN_a_turn_WHEN_its_chat_is_locked_THEN_a_turn_is_running_until_released():
    activity = HubTurnActivity()
    registry = SessionLockRegistry(turn_activity=activity)
    assert not activity.any_running()

    async with registry.acquire("chat-1"):
        assert activity.any_running()

    assert not activity.any_running()


@pytest.mark.asyncio
async def test_GIVEN_two_chats_WHEN_one_finishes_THEN_the_other_still_counts():
    activity = HubTurnActivity()
    registry = SessionLockRegistry(turn_activity=activity)

    assert await registry.try_acquire("chat-1")
    assert await registry.try_acquire("chat-2")
    await registry.release("chat-1")
    assert activity.any_running()
    await registry.release("chat-2")
    assert not activity.any_running()


@pytest.mark.asyncio
async def test_GIVEN_a_busy_chat_WHEN_a_second_turn_is_refused_or_released_twice_THEN_the_count_holds():
    activity = HubTurnActivity()
    registry = SessionLockRegistry(turn_activity=activity)

    assert await registry.try_acquire("chat-1")
    assert not await registry.try_acquire("chat-1")
    await registry.release("chat-1")
    await registry.release("chat-1")

    assert not activity.any_running()
    assert await registry.try_acquire("chat-1")
    assert activity.any_running()


@pytest.mark.asyncio
async def test_GIVEN_a_listener_WHEN_a_turn_starts_THEN_it_is_told():
    activity = HubTurnActivity()
    registry = SessionLockRegistry(turn_activity=activity)
    started = []
    stop = activity.on_turn_start(lambda: started.append(True))

    assert await registry.try_acquire("chat-1")
    stop()
    await registry.release("chat-1")
    assert await registry.try_acquire("chat-1")

    assert started == [True]


def test_GIVEN_work_after_a_turn_WHEN_asked_THEN_a_turn_counts_as_running():
    busy = {"value": True}
    activity = HubTurnActivity(after_turn_busy=lambda: busy["value"])

    assert activity.any_running()
    busy["value"] = False
    assert not activity.any_running()
