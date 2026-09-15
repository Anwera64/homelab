import asyncio
import json
import math
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
from typing import AsyncIterator, Awaitable, Callable, Optional, TypeVar

from app.domain.entities.pin_lockout import lockout_seconds_after
from app.domain.exceptions import CodeGuessesLockedException, InviteInvalidException
from app.domain.repositories.system_setting_repository import ISystemSettingRepository
from app.domain.repositories.unit_of_work import IUnitOfWork

SETTING_KEY = "code_guesses"

T = TypeVar("T")


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


class CodeGuessLock:
    """
    Held from reading the misses to recording the outcome, so guesses fired together can't all read
    the same count. In memory, because the hub runs as a single process; the lock is made when a
    guess needs it and dropped once nobody holds or waits for it, as `MemberPinLocks` does.
    """

    def __init__(self):
        self._lock: Optional[asyncio.Lock] = None
        self._holders = 0

    @asynccontextmanager
    async def hold(self) -> AsyncIterator[None]:
        if self._lock is None:
            self._lock = asyncio.Lock()
        lock = self._lock
        self._holders += 1
        try:
            async with lock:
                yield
        finally:
            self._holders -= 1
            if self._holders == 0:
                self._lock = None


class GuardCodeGuessesUseCase:
    """
    Puts invite and reset codes under the PIN lockout's schedule, hub-wide: a guessed code names no
    member to key a lockout on. Five wrong codes are refused as they are; the fifth starts a wait,
    and while it lasts no code is checked at all. A right code clears the misses. They are kept in
    the system settings, so a restart doesn't hand out five fresh guesses.
    """

    def __init__(
        self,
        settings: ISystemSettingRepository,
        uow: IUnitOfWork,
        lock: CodeGuessLock,
        clock: Callable[[], datetime] = _utc_now,
    ):
        self.settings = settings
        self.uow = uow
        self.lock = lock
        self.clock = clock

    async def execute(self, attempt: Callable[[], Awaitable[T]]) -> T:
        async with self.lock.hold():
            failures, locked_until = await self._load()
            now = self.clock()
            if locked_until and now < locked_until:
                raise CodeGuessesLockedException(math.ceil((locked_until - now).total_seconds()))

            try:
                result = await attempt()
            except InviteInvalidException:
                failures += 1
                wait = lockout_seconds_after(failures)
                await self._save(failures, now + timedelta(seconds=wait) if wait is not None else None)
                if wait is not None:
                    raise CodeGuessesLockedException(wait)
                raise

            if failures or locked_until:
                await self._save(0, None)
            return result

    async def _load(self) -> tuple[int, Optional[datetime]]:
        setting = await self.settings.get(SETTING_KEY)
        if not setting:
            return 0, None
        state = json.loads(setting.value)
        locked_until = state.get("locked_until")
        return state.get("failures", 0), datetime.fromisoformat(locked_until) if locked_until else None

    async def _save(self, failures: int, locked_until: Optional[datetime]) -> None:
        value = json.dumps({
            "failures": failures,
            "locked_until": locked_until.isoformat() if locked_until else None,
        })
        async with self.uow:
            await self.settings.set(SETTING_KEY, value)
            await self.uow.commit()
