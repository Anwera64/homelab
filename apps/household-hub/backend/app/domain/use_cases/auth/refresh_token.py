from datetime import datetime, timezone
from typing import Callable

from app.domain.repositories.device_session_repository import IDeviceSessionRepository
from app.domain.repositories.security_service import ITokenService
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.use_cases.auth.authenticate_token import SignedInMember
from app.domain.use_cases.auth.sign_in_device import SignInDeviceUseCase


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


class RefreshTokenUseCase:
    """
    Re-issues a signed-in member's token with a fresh expiry, so a phone in use never reaches the
    end of its 30 days. The caller has already been authenticated: an expired token, one from an
    older token version, one from a device that signed out, or an inactive member never gets this far.

    The new token names the same device as the old one, so signing that device out refuses both.
    A token from before devices were tracked names none; refreshing it is when its device gets one.
    """

    def __init__(
        self,
        token_service: ITokenService,
        device_session_repo: IDeviceSessionRepository,
        sign_in_device: SignInDeviceUseCase,
        uow: IUnitOfWork,
        clock: Callable[[], datetime] = _utc_now,
    ):
        self.token_service = token_service
        self.device_session_repo = device_session_repo
        self.sign_in_device = sign_in_device
        self.uow = uow
        self.clock = clock

    async def execute(self, signed_in: SignedInMember) -> dict:
        user = signed_in.user
        if not signed_in.device_session_id:
            return await self.sign_in_device.execute(user)

        async with self.uow:
            await self.device_session_repo.touch(signed_in.device_session_id, self.clock())
            await self.uow.commit()

        token = self.token_service.create_access_token(
            subject=user.id,
            is_admin=user.is_admin,
            token_version=user.token_version,
            session_id=signed_in.device_session_id,
        )
        return {
            "access_token": token,
            "token_type": "bearer",
            "user": user,
        }
