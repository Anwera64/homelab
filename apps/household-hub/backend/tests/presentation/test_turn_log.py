"""
A turn's events, numbered and kept, so a phone that dropped off can ask for the rest.

The phone locks, the socket dies, the hub keeps writing. What it wrote in the meantime has to be
somewhere the phone can come back to, in order and exactly once — "everything after 41" is only a
question worth asking if 42 means the same event both times.
"""

import asyncio
import gc

import pytest

from app.presentation.api.turn_log import TurnLog, TurnLogRegistry


async def _drain(log: TurnLog, after: int = 0) -> list[tuple[int, dict]]:
    return [pair async for pair in log.follow(after)]


@pytest.mark.asyncio
async def test_following_from_the_start_yields_every_event_in_order():
    """GIVEN a closed turn of three events WHEN followed from 0 THEN all three arrive numbered 1..3."""
    log = TurnLog()
    for n in range(3):
        await log.put({"type": "delta", "content": str(n)})
    await log.put(None)

    assert await _drain(log) == [
        (1, {"type": "delta", "content": "0"}),
        (2, {"type": "delta", "content": "1"}),
        (3, {"type": "delta", "content": "2"}),
    ]


@pytest.mark.asyncio
async def test_following_after_an_event_skips_what_was_already_seen():
    """GIVEN a closed turn of four events WHEN followed after 2 THEN only 3 and 4 arrive."""
    log = TurnLog()
    for n in range(4):
        await log.put({"n": n})
    await log.put(None)

    assert [seq for seq, _ in await _drain(log, after=2)] == [3, 4]


@pytest.mark.asyncio
async def test_a_follower_waiting_on_a_running_turn_gets_new_events_live():
    """GIVEN a follower attached before anything was said WHEN events arrive THEN it receives them."""
    log = TurnLog()
    follower = asyncio.create_task(_drain(log))
    await asyncio.sleep(0)

    await log.put({"type": "accepted"})
    await asyncio.sleep(0)
    await log.put({"type": "delta", "content": "hi"})
    await log.put(None)

    assert await asyncio.wait_for(follower, timeout=1) == [
        (1, {"type": "accepted"}),
        (2, {"type": "delta", "content": "hi"}),
    ]


@pytest.mark.asyncio
async def test_each_turn_has_its_own_id():
    """GIVEN two turns WHEN compared THEN their ids differ, so a stale resume can be told apart."""
    assert TurnLog().turn_id != TurnLog().turn_id


@pytest.mark.asyncio
async def test_a_new_turn_replaces_the_last_one_for_that_conversation():
    """GIVEN a turn already logged WHEN another starts on the same conversation THEN get returns the new one."""
    registry = TurnLogRegistry()
    first = registry.start("sess-1")
    second = registry.start("sess-1")

    assert registry.get("sess-1") is second
    assert second is not first


@pytest.mark.asyncio
async def test_a_finished_turn_is_kept_for_a_while_and_then_forgotten():
    """GIVEN a closed turn WHEN read inside retention THEN it is there, and after it THEN it is gone."""
    now = [1000.0]
    registry = TurnLogRegistry(clock=lambda: now[0], retention_s=300)
    log = registry.start("sess-1")
    await log.put(None)

    now[0] += 299
    assert registry.get("sess-1") is log

    now[0] += 2
    assert registry.get("sess-1") is None


@pytest.mark.asyncio
async def test_a_running_turn_is_never_forgotten_however_long_it_takes():
    """GIVEN an open turn WHEN read long after it started THEN it is still there."""
    now = [1000.0]
    registry = TurnLogRegistry(clock=lambda: now[0], retention_s=300)
    log = registry.start("sess-1")

    now[0] += 10_000
    assert registry.get("sess-1") is log


@pytest.mark.asyncio
async def test_a_spawned_worker_is_held_until_it_finishes():
    """GIVEN a spawned worker nobody else references WHEN collected THEN it still runs to the end."""
    registry = TurnLogRegistry()
    finished = asyncio.Event()

    async def worker():
        await asyncio.sleep(0.01)
        finished.set()

    registry.spawn(worker())
    gc.collect()

    await asyncio.wait_for(finished.wait(), timeout=1)
    await asyncio.sleep(0)
    assert registry.running_workers == 0
