"""
Invite and reset codes are guessable without a limit, and a guessed code names no member to lock.
So one guard covers the whole hub, on the PIN lockout's schedule: five free misses, then a wait that
starts at 30 seconds and doubles on every further miss, up to 15 minutes. A right code clears it.
"""
import asyncio
from datetime import datetime, timedelta, timezone

import pytest

from app.domain.entities.system_setting import SystemSetting
from app.domain.exceptions import CodeGuessesLockedException, InviteInvalidException, NameTakenException
from app.domain.use_cases.auth.guard_code_guesses import CodeGuessLock, GuardCodeGuessesUseCase


class Clock:
    def __init__(self):
        self.now = datetime(2026, 9, 15, 12, 0, tzinfo=timezone.utc)

    def __call__(self) -> datetime:
        return self.now

    def advance(self, seconds: int) -> None:
        self.now += timedelta(seconds=seconds)


class YieldingSettings:
    """Awaits on every call, like a real database does, so concurrent guesses can interleave."""

    def __init__(self):
        self.values: dict[str, str] = {}

    async def get(self, key: str):
        await asyncio.sleep(0)
        return SystemSetting(key=key, value=self.values[key]) if key in self.values else None

    async def set(self, key: str, value: str) -> SystemSetting:
        await asyncio.sleep(0)
        self.values[key] = value
        return SystemSetting(key=key, value=value)


class FakeUow:
    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        pass

    async def commit(self):
        pass


class Attempts:
    """A code check that counts how often it ran."""

    def __init__(self, outcome=None):
        self.calls = 0
        self.outcome = outcome

    async def __call__(self):
        self.calls += 1
        await asyncio.sleep(0)
        if isinstance(self.outcome, Exception):
            raise self.outcome
        return self.outcome


def wrong_code() -> Attempts:
    return Attempts(InviteInvalidException("That code isn't valid."))


def guard(settings: YieldingSettings, clock: Clock, lock: CodeGuessLock | None = None) -> GuardCodeGuessesUseCase:
    return GuardCodeGuessesUseCase(settings, FakeUow(), lock or CodeGuessLock(), clock)


async def miss(g: GuardCodeGuessesUseCase, times: int) -> None:
    for _ in range(times):
        with pytest.raises((InviteInvalidException, CodeGuessesLockedException)):
            await g.execute(wrong_code())


@pytest.mark.asyncio
async def test_the_first_four_wrong_codes_are_only_refused():
    g = guard(YieldingSettings(), Clock())

    for _ in range(4):
        with pytest.raises(InviteInvalidException):
            await g.execute(wrong_code())


@pytest.mark.asyncio
async def test_the_fifth_wrong_code_makes_everyone_wait_30_seconds():
    g = guard(YieldingSettings(), Clock())
    await miss(g, 4)

    with pytest.raises(CodeGuessesLockedException) as locked:
        await g.execute(wrong_code())

    assert locked.value.retry_after_seconds == 30


@pytest.mark.asyncio
async def test_while_waiting_not_even_a_right_code_is_tried():
    clock = Clock()
    g = guard(YieldingSettings(), clock)
    await miss(g, 5)
    clock.advance(10)
    right = Attempts("joined")

    with pytest.raises(CodeGuessesLockedException) as locked:
        await g.execute(right)

    assert locked.value.retry_after_seconds == 20
    assert right.calls == 0


@pytest.mark.asyncio
async def test_a_miss_after_the_wait_locks_for_twice_as_long():
    clock = Clock()
    g = guard(YieldingSettings(), clock)
    await miss(g, 5)
    clock.advance(30)

    with pytest.raises(CodeGuessesLockedException) as locked:
        await g.execute(wrong_code())

    assert locked.value.retry_after_seconds == 60


@pytest.mark.asyncio
async def test_a_right_code_answers_and_clears_the_misses():
    clock = Clock()
    g = guard(YieldingSettings(), clock)
    await miss(g, 4)

    assert await g.execute(Attempts("joined")) == "joined"

    await miss(g, 4)
    with pytest.raises(CodeGuessesLockedException):
        await g.execute(wrong_code())


@pytest.mark.asyncio
async def test_the_misses_are_kept_where_a_new_guard_finds_them():
    settings, clock = YieldingSettings(), Clock()
    await miss(guard(settings, clock), 4)

    with pytest.raises(CodeGuessesLockedException):
        await guard(settings, clock).execute(wrong_code())


@pytest.mark.asyncio
async def test_other_refusals_pass_through_without_counting():
    g = guard(YieldingSettings(), Clock())

    for _ in range(6):
        with pytest.raises(NameTakenException):
            await g.execute(Attempts(NameTakenException("Someone in the household already has that name.")))

    await miss(g, 4)


@pytest.mark.asyncio
async def test_guesses_fired_together_are_still_checked_one_at_a_time():
    g = guard(YieldingSettings(), Clock())
    attempt = wrong_code()

    outcomes = await asyncio.gather(*(g.execute(attempt) for _ in range(10)), return_exceptions=True)

    assert attempt.calls == 5
    assert sum(isinstance(o, CodeGuessesLockedException) for o in outcomes) == 6
