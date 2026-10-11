from datetime import datetime, timedelta, timezone
from typing import Callable

from app.domain.repositories.device_session_repository import IDeviceSessionRepository
from app.domain.repositories.unit_of_work import IUnitOfWork


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


class PurgeExpiredDeviceSessionsUseCase:
    """
    Forgets the devices nobody came back to. A device was last given a token when it was last seen,
    so once a token's lifetime has passed since then, nothing it holds is accepted any more and its
    session only takes up room.
    """

    def __init__(
        self,
        device_session_repo: IDeviceSessionRepository,
        uow: IUnitOfWork,
        token_lifetime: timedelta,
        clock: Callable[[], datetime] = _utc_now,
    ):
        self.device_session_repo = device_session_repo
        self.uow = uow
        self.token_lifetime = token_lifetime
        self.clock = clock

    async def execute(self) -> int:
        """Returns how many were forgotten."""
        async with self.uow:
            removed = await self.device_session_repo.delete_last_seen_before(self.clock() - self.token_lifetime)
            await self.uow.commit()
        return removed
