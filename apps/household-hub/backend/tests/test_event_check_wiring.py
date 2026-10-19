"""The periodic check for events changed outside the hub (memory spec §7) runs on its own schedule."""

import asyncio
from contextlib import asynccontextmanager

import pytest

from app.bootstrap import app_factory
from app.core.config import Settings
from app.domain.use_cases.memories.follow_event import CheckEventMemoriesUseCase


@pytest.mark.asyncio
async def test_GIVEN_the_event_checker_WHEN_it_wakes_THEN_it_runs_a_pass_and_survives_a_failing_one(monkeypatch):
    passes = []

    class Check:
        async def run_pass(self):
            passes.append(1)
            if len(passes) == 1:
                raise RuntimeError("database locked")

    @asynccontextmanager
    async def fake_session():
        yield None

    monkeypatch.setattr(app_factory, "AsyncSessionLocal", fake_session)
    monkeypatch.setattr(app_factory, "get_container", lambda session: {CheckEventMemoriesUseCase: Check()})

    task = asyncio.create_task(app_factory.periodic_event_checker(interval_seconds=0.01))
    for _ in range(100):
        if len(passes) >= 2:
            break
        await asyncio.sleep(0.01)
    task.cancel()
    await asyncio.gather(task, return_exceptions=True)

    assert len(passes) >= 2


def test_GIVEN_no_override_THEN_events_are_checked_every_30_minutes(monkeypatch):
    monkeypatch.delenv("EVENT_CHECK_MINUTES", raising=False)
    assert Settings(_env_file=None).EVENT_CHECK_MINUTES == 30
