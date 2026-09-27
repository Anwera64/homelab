"""
The work a turn leaves behind once its answer is sent: reflection, then the history summary (#53).

It runs after the chat's lock is let go, so it needs an order of its own: one chat's jobs one at a
time, in the order they were handed over, so two summaries never rewrite the same history at once.
Different chats don't wait for each other.
"""
import asyncio

import pytest

from app.bootstrap.after_turn import AfterTurnWork


async def _settle(work: AfterTurnWork, timeout: float = 2.0) -> None:
    async def wait():
        while work.running:
            await asyncio.sleep(0.01)
    await asyncio.wait_for(wait(), timeout)


@pytest.mark.asyncio
async def test_one_chats_jobs_run_one_at_a_time_in_order():
    work = AfterTurnWork()
    log: list[str] = []

    def job(name: str):
        async def run():
            log.append(f"{name} starts")
            await asyncio.sleep(0.02)
            log.append(f"{name} ends")
        return run

    work.spawn("chat", job("first"))
    work.spawn("chat", job("second"))
    await _settle(work)

    assert log == ["first starts", "first ends", "second starts", "second ends"]


@pytest.mark.asyncio
async def test_different_chats_do_not_wait_for_each_other():
    work = AfterTurnWork()
    release = asyncio.Event()
    finished: list[str] = []

    async def stuck():
        await release.wait()
        finished.append("stuck")

    async def quick():
        finished.append("quick")

    work.spawn("busy chat", stuck)
    work.spawn("other chat", quick)
    await asyncio.sleep(0.05)

    assert finished == ["quick"]
    release.set()
    await _settle(work)


@pytest.mark.asyncio
async def test_a_failing_job_does_not_stop_the_next():
    work = AfterTurnWork()
    ran: list[str] = []

    async def fails():
        raise RuntimeError("model went away")

    async def next_one():
        ran.append("next")

    work.spawn("chat", fails)
    work.spawn("chat", next_one)
    await _settle(work)

    assert ran == ["next"]


@pytest.mark.asyncio
async def test_an_idle_chat_leaves_nothing_behind():
    work = AfterTurnWork()

    async def job():
        pass

    work.spawn("chat", job)
    work.spawn("chat", job)
    await _settle(work)

    assert work.running == 0
    assert not work.is_busy("chat")
    assert work.chats_held == 0


@pytest.mark.asyncio
async def test_a_chat_with_work_left_says_so():
    work = AfterTurnWork()
    release = asyncio.Event()

    async def stuck():
        await release.wait()

    work.spawn("chat", stuck)
    await asyncio.sleep(0)

    assert work.is_busy("chat")
    assert not work.is_busy("another chat")
    release.set()
    await _settle(work)
