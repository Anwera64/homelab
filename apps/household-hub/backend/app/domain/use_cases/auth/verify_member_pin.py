import asyncio
import math
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
from typing import AsyncIterator, Callable, Dict

from app.domain.entities.pin_lockout import attempts_left_after, lockout_seconds_after
from app.domain.entities.user import User
from app.domain.exceptions import AuthenticationException, PinLockedException, WrongPinException
from app.domain.repositories.security_service import IPasswordHasher
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.repositories.user_repository import IUserRepository


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


class _Holders:
    def __init__(self):
        self.lock = asyncio.Lock()
        self.count = 0


class MemberPinLocks:
    """
    One lock per member, held from reading their misses to recording the outcome, so guesses fired
    together can't all read the same count. In memory, because the hub runs as a single process;
    a member's entry is dropped once nobody holds or waits for it.
    """

    def __init__(self):
        self._holders: Dict[str, _Holders] = {}

    @asynccontextmanager
    async def hold(self, user_id: str) -> AsyncIterator[None]:
        holders = self._holders.setdefault(user_id, _Holders())
        holders.count += 1
        try:
            async with holders.lock:
                yield
        finally:
            holders.count -= 1
            if holders.count == 0:
                del self._holders[user_id]


class VerifyMemberPinUseCase:
    """
    Checks a member's PIN under the rules in `pin_lockout`: a miss says how many tries are left,
    the fifth starts a wait, and while the wait lasts the PIN isn't checked at all.
    Sign-in uses it now; unlocking secret chats will share it.
    """

    def __init__(
        self,
        user_repo: IUserRepository,
        hasher: IPasswordHasher,
        uow: IUnitOfWork,
        dummy_hash: str,
        locks: MemberPinLocks,
        clock: Callable[[], datetime] = _utc_now,
    ):
        self.user_repo = user_repo
        self.hasher = hasher
        self.uow = uow
        self.dummy_hash = dummy_hash
        self.locks = locks
        self.clock = clock

    async def execute(self, user_id: str, pin: str) -> User:
        async with self.locks.hold(user_id):
            user = await self.user_repo.get_by_id(user_id)
            if not user or not user.is_active:
                # Same bcrypt cost as a real check, so the time taken doesn't say who exists.
                self.hasher.verify(pin, self.dummy_hash)
                raise AuthenticationException("Wrong PIN")

            now = self.clock()
            if user.pin_locked_until and now < user.pin_locked_until:
                raise PinLockedException(math.ceil((user.pin_locked_until - now).total_seconds()))

            if self.hasher.verify(pin, user.hashed_pin):
                if user.failed_pin_attempts or user.pin_locked_until:
                    user.failed_pin_attempts = 0
                    user.pin_locked_until = None
                    await self._save(user)
                return user

            user.failed_pin_attempts += 1
            wait = lockout_seconds_after(user.failed_pin_attempts)
            if wait is not None:
                user.pin_locked_until = now + timedelta(seconds=wait)
            await self._save(user)

            if wait is not None:
                raise PinLockedException(wait)
            raise WrongPinException(attempts_left_after(user.failed_pin_attempts))

    async def _save(self, user: User) -> None:
        async with self.uow:
            await self.user_repo.update(user)
            await self.uow.commit()
