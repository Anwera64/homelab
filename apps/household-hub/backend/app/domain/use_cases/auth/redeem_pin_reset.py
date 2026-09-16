from datetime import datetime, timezone
from typing import Callable

from app.domain.entities.one_time_code import normalise_code
from app.domain.exceptions import InviteInvalidException
from app.domain.repositories.pin_reset_repository import IPinResetRepository
from app.domain.repositories.security_service import IPasswordHasher, ITokenService
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.repositories.user_repository import IUserRepository
from app.domain.use_cases.auth.guard_code_guesses import GuardCodeGuessesUseCase


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


class RedeemPinResetUseCase:
    """
    The member reads out the code they were given and chooses a new PIN. Their misses are forgiven —
    the lockout is what sent them here — and the token version moves on, because a PIN they no longer
    know may be on another device.
    """

    def __init__(
        self,
        pin_reset_repo: IPinResetRepository,
        user_repo: IUserRepository,
        hasher: IPasswordHasher,
        uow: IUnitOfWork,
        token_service: ITokenService,
        guard: GuardCodeGuessesUseCase,
        clock: Callable[[], datetime] = _utc_now,
    ):
        self.pin_reset_repo = pin_reset_repo
        self.user_repo = user_repo
        self.hasher = hasher
        self.uow = uow
        self.token_service = token_service
        self.guard = guard
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
            await self.uow.commit()

        token = self.token_service.create_access_token(
            subject=user.id, is_admin=user.is_admin, token_version=user.token_version
        )
        return {
            "access_token": token,
            "token_type": "bearer",
            "user": user,
        }
