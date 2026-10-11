from datetime import datetime, timezone
from typing import Callable

from app.domain.entities.device_session import DeviceSession
from app.domain.entities.user import User
from app.domain.repositories.device_session_repository import IDeviceSessionRepository
from app.domain.repositories.security_service import ITokenService
from app.domain.repositories.unit_of_work import IUnitOfWork


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


class SignInDeviceUseCase:
    """
    The last step of every way of signing in — PIN, invite, PIN reset: the device gets a session of
    its own and a token that names it. Whoever calls this has already established who the member is.
    """

    def __init__(
        self,
        device_session_repo: IDeviceSessionRepository,
        token_service: ITokenService,
        uow: IUnitOfWork,
        clock: Callable[[], datetime] = _utc_now,
    ):
        self.device_session_repo = device_session_repo
        self.token_service = token_service
        self.uow = uow
        self.clock = clock

    async def execute(self, user: User) -> dict:
        now = self.clock()
        async with self.uow:
            device = await self.device_session_repo.create(
                DeviceSession(user_id=user.id, created_at=now, last_seen_at=now)
            )
            await self.uow.commit()

        token = self.token_service.create_access_token(
            subject=user.id, is_admin=user.is_admin, token_version=user.token_version, session_id=device.id
        )
        return {
            "access_token": token,
            "token_type": "bearer",
            "user": user,
        }
