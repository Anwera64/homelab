"""
A six-digit PIN is only sound with a limit on guessing: five free tries, then a wait that starts
at 30 seconds and doubles on every further miss, up to 15 minutes. A right PIN clears it.
"""
import asyncio
from datetime import datetime, timedelta, timezone

import pytest

from app.domain.entities.pin_lockout import attempts_left_after, lockout_seconds_after
from app.domain.entities.user import User
from app.domain.exceptions import AuthenticationException, PinLockedException, WrongPinException
from app.domain.use_cases.auth.verify_member_pin import MemberPinLocks, VerifyMemberPinUseCase

RIGHT_PIN = "482913"
WRONG_PIN = "000000"
DUMMY_HASH = "hashed_dummy"


class Clock:
    def __init__(self):
        self.now = datetime(2026, 9, 13, 12, 0, tzinfo=timezone.utc)

    def __call__(self) -> datetime:
        return self.now

    def advance(self, seconds: int) -> None:
        self.now += timedelta(seconds=seconds)


class CountingHasher:
    def __init__(self):
        self.checked_against: list[str] = []

    def hash(self, pin: str) -> str:
        return f"hashed_{pin}"

    def verify(self, plain: str, hashed: str) -> bool:
        self.checked_against.append(hashed)
        return hashed == f"hashed_{plain}"


class YieldingUsers:
    """Awaits on every call, like a real database does, so concurrent guesses can interleave."""

    def __init__(self, *users: User):
        self.users = {u.id: u for u in users}

    async def get_by_id(self, user_id: str):
        await asyncio.sleep(0)
        user = self.users.get(user_id)
        return User(**vars(user)) if user else None

    async def update(self, user: User) -> User:
        await asyncio.sleep(0)
        self.users[user.id] = User(**vars(user))
        return user


class FakeUow:
    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        pass

    async def commit(self):
        pass


def member(**overrides) -> User:
    return User(**{"id": "emma", "full_name": "Emma", "hashed_pin": f"hashed_{RIGHT_PIN}", **overrides})


def verifier(users: YieldingUsers, clock: Clock, hasher: CountingHasher | None = None) -> VerifyMemberPinUseCase:
    return VerifyMemberPinUseCase(
        user_repo=users,
        hasher=hasher or CountingHasher(),
        uow=FakeUow(),
        dummy_hash=DUMMY_HASH,
        locks=MemberPinLocks(),
        clock=clock,
    )


@pytest.mark.parametrize("failures, left", [(1, 4), (2, 3), (3, 2), (4, 1)])
def test_the_first_four_misses_only_count_down(failures, left):
    assert lockout_seconds_after(failures) is None
    assert attempts_left_after(failures) == left


@pytest.mark.parametrize("failures, seconds", [(5, 30), (6, 60), (7, 120), (8, 240), (9, 480), (10, 900), (15, 900)])
def test_the_wait_starts_at_30_seconds_and_doubles_up_to_15_minutes(failures, seconds):
    assert lockout_seconds_after(failures) == seconds


@pytest.mark.asyncio
async def test_a_right_pin_returns_the_member_and_clears_their_misses():
    users = YieldingUsers(member(failed_pin_attempts=3))

    signed_in = await verifier(users, Clock()).execute("emma", RIGHT_PIN)

    assert signed_in.id == "emma"
    assert users.users["emma"].failed_pin_attempts == 0


@pytest.mark.asyncio
async def test_a_wrong_pin_says_how_many_attempts_are_left():
    users = YieldingUsers(member())

    with pytest.raises(WrongPinException) as miss:
        await verifier(users, Clock()).execute("emma", WRONG_PIN)

    assert miss.value.attempts_left == 4
    assert users.users["emma"].failed_pin_attempts == 1


@pytest.mark.asyncio
async def test_the_fifth_miss_locks_the_member_for_30_seconds():
    clock = Clock()
    users = YieldingUsers(member(failed_pin_attempts=4))

    with pytest.raises(PinLockedException) as locked:
        await verifier(users, clock).execute("emma", WRONG_PIN)

    assert locked.value.retry_after_seconds == 30
    assert users.users["emma"].pin_locked_until == clock.now + timedelta(seconds=30)


@pytest.mark.asyncio
async def test_while_locked_not_even_the_right_pin_is_checked():
    clock = Clock()
    hasher = CountingHasher()
    users = YieldingUsers(member(failed_pin_attempts=5, pin_locked_until=clock.now + timedelta(seconds=30)))
    clock.advance(10)

    with pytest.raises(PinLockedException) as locked:
        await verifier(users, clock, hasher).execute("emma", RIGHT_PIN)

    assert locked.value.retry_after_seconds == 20
    assert hasher.checked_against == []


@pytest.mark.asyncio
async def test_once_the_wait_is_over_the_right_pin_works_and_clears_the_lock():
    clock = Clock()
    users = YieldingUsers(member(failed_pin_attempts=5, pin_locked_until=clock.now + timedelta(seconds=30)))
    clock.advance(30)

    await verifier(users, clock).execute("emma", RIGHT_PIN)

    assert users.users["emma"].failed_pin_attempts == 0
    assert users.users["emma"].pin_locked_until is None


@pytest.mark.asyncio
async def test_a_miss_after_the_wait_locks_for_twice_as_long():
    clock = Clock()
    users = YieldingUsers(member(failed_pin_attempts=5, pin_locked_until=clock.now + timedelta(seconds=30)))
    clock.advance(30)

    with pytest.raises(PinLockedException) as locked:
        await verifier(users, clock).execute("emma", WRONG_PIN)

    assert locked.value.retry_after_seconds == 60


@pytest.mark.asyncio
@pytest.mark.parametrize("users", [YieldingUsers(), YieldingUsers(member(is_active=False))])
async def test_unknown_and_inactive_members_are_refused_after_the_same_hash_check(users):
    hasher = CountingHasher()

    with pytest.raises(AuthenticationException) as refused:
        await verifier(users, Clock(), hasher).execute("emma", RIGHT_PIN)

    assert type(refused.value) is AuthenticationException
    assert hasher.checked_against == [DUMMY_HASH]


@pytest.mark.asyncio
async def test_guesses_fired_together_are_still_checked_one_at_a_time():
    hasher = CountingHasher()
    users = YieldingUsers(member())
    verify = verifier(users, Clock(), hasher)

    outcomes = await asyncio.gather(
        *(verify.execute("emma", WRONG_PIN) for _ in range(10)),
        return_exceptions=True,
    )

    assert len(hasher.checked_against) == 5
    assert users.users["emma"].failed_pin_attempts == 5
    assert sum(isinstance(o, PinLockedException) for o in outcomes) == 6
