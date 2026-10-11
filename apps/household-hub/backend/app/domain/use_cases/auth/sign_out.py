import logging

from app.domain.repositories.device_session_repository import IDeviceSessionRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.use_cases.auth.authenticate_token import SignedInMember

logger = logging.getLogger(__name__)


class SignOutUseCase:
    """
    The member signs out on one device. That device's session is forgotten, which refuses every
    token it was ever given — the one it holds and any it refreshed away — and no other device's.

    A token from before devices were tracked names none, so there is nothing to forget: it lives
    until it expires, as every token did then. The sign-out is logged either way, which is how it
    reaches the logs at all; the phone has dropped its token before it could report anything.
    """

    def __init__(self, device_session_repo: IDeviceSessionRepository, uow: IUnitOfWork):
        self.device_session_repo = device_session_repo
        self.uow = uow

    async def execute(self, signed_in: SignedInMember) -> None:
        if signed_in.device_session_id:
            async with self.uow:
                await self.device_session_repo.delete(signed_in.device_session_id)
                await self.uow.commit()

        logger.info(
            "Signed out | member=%s device=%s", signed_in.user.id, signed_in.device_session_id or "none"
        )
