from datetime import datetime, timezone
from typing import Callable

from app.domain.entities.one_time_code import CODE_LIFETIME, new_code
from app.domain.entities.pin_reset import PinReset
from app.domain.entities.user import User
from app.domain.exceptions import (
    EntityNotFoundException,
    OwnPinResetException,
    WrongConfirmationPinException,
    WrongPinException,
)
from app.domain.repositories.pin_reset_repository import IPinResetRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.repositories.user_repository import IUserRepository
from app.domain.use_cases.auth.verify_member_pin import VerifyMemberPinUseCase


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


class ApprovePinResetUseCase:
    """
    One member vouches for another: they confirm with their own PIN and read out a code. Recovery is
    social because there is no mail server, and any member can vouch for any other — deliberately not
    "the admin resets it", which would strand the admin.
    """

    def __init__(
        self,
        user_repo: IUserRepository,
        pin_reset_repo: IPinResetRepository,
        verify_pin: VerifyMemberPinUseCase,
        uow: IUnitOfWork,
        clock: Callable[[], datetime] = _utc_now,
        make_code: Callable[[], str] = new_code,
    ):
        self.user_repo = user_repo
        self.pin_reset_repo = pin_reset_repo
        self.verify_pin = verify_pin
        self.uow = uow
        self.clock = clock
        self.make_code = make_code

    async def execute(self, approver: User, target_user_id: str, pin: str) -> PinReset:
        if approver.id == target_user_id:
            raise OwnPinResetException("Someone else has to approve a new PIN for you.")

        target = await self.user_repo.get_by_id(target_user_id)
        if not target or not target.is_active:
            raise EntityNotFoundException("Member not found")

        try:
            await self.verify_pin.execute(approver.id, pin)
        except WrongPinException as e:
            raise WrongConfirmationPinException(e.attempts_left) from e

        now = self.clock()
        reset = PinReset(
            code=self.make_code(),
            target_user_id=target_user_id,
            approver_id=approver.id,
            expires_at=now + CODE_LIFETIME,
            created_at=now,
        )
        async with self.uow:
            await self.pin_reset_repo.delete_unused_for_target(target_user_id)
            created = await self.pin_reset_repo.create(reset)
            await self.uow.commit()
        return created
