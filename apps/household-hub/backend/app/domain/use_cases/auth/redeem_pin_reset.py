from datetime import datetime, timezone
from typing import Callable

from app.domain.entities.one_time_code import normalise_code
from app.domain.exceptions import InviteInvalidException
from app.domain.repositories.device_session_repository import IDeviceSessionRepository
from app.domain.repositories.pin_reset_repository import IPinResetRepository
from app.domain.repositories.security_service import IPasswordHasher
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.repositories.user_repository import IUserRepository
from app.domain.use_cases.auth.guard_code_guesses import GuardCodeGuessesUseCase
from app.domain.use_cases.auth.sign_in_device import SignInDeviceUseCase


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


class RedeemPinResetUseCase:
    """
    The member reads out the code they were given and chooses a new PIN. Their misses are forgiven —
    the lockout is what sent them here — and the token version moves on, because a PIN they no longer
    know may be on another device. Every device they were signed in on is forgotten; the one redeeming
    the code is signed in afresh.
    """

    def __init__(
        self,
        pin_reset_repo: IPinResetRepository,
        user_repo: IUserRepository,
        hasher: IPasswordHasher,
        uow: IUnitOfWork,
        sign_in_device: SignInDeviceUseCase,
        guard: GuardCodeGuessesUseCase,
        device_session_repo: IDeviceSessionRepository,
        clock: Callable[[], datetime] = _utc_now,
    ):
        self.pin_reset_repo = pin_reset_repo
        self.user_repo = user_repo
        self.hasher = hasher
        self.uow = uow
        self.sign_in_device = sign_in_device
        self.guard = guard
        self.device_session_repo = device_session_repo
        self.clock = clock

    async def execute(self, code: str, pin: str) -> dict:
        return await self.guard.execute(lambda: self._redeem(code, pin))

    async def _redeem(self, code: str, pin: str) -> dict:
        now = self.clock()
        refused = InviteInvalidException("That reset code isn't valid.")

        reset = await self.pin_reset_repo.get_by_code(normalise_code(code))
        if not reset or not reset.is_usable_at(now):
            raise refused

        user = await self.user_repo.get_by_id(reset.target_user_id)
        if not user or not user.is_active:
            raise refused

        if not await self.pin_reset_repo.claim(reset.id, now):
            raise refused

        user.hashed_pin = self.hasher.hash(pin)
        user.failed_pin_attempts = 0
        user.pin_locked_until = None
        user.token_version += 1
        async with self.uow:
            await self.user_repo.update(user)
            await self.device_session_repo.delete_for_user(user.id)
            await self.uow.commit()

        return await self.sign_in_device.execute(user)
