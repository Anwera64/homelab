from datetime import datetime, timezone
from typing import Callable

from app.domain.entities.invite import Invite
from app.domain.entities.one_time_code import CODE_LIFETIME, new_code
from app.domain.entities.user import User
from app.domain.repositories.invite_repository import IInviteRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.repositories.user_repository import IUserRepository
from app.domain.use_cases.users.member_names import raise_if_name_taken


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


class CreateInviteUseCase:
    """
    An admin names who's joining and gets a one-time code. The admin never invents or relays a
    credential: the joiner picks their own PIN when they redeem it. Asking again for the same name
    retires the code given before.
    """

    def __init__(
        self,
        user_repo: IUserRepository,
        invite_repo: IInviteRepository,
        uow: IUnitOfWork,
        clock: Callable[[], datetime] = _utc_now,
        make_code: Callable[[], str] = new_code,
    ):
        self.user_repo = user_repo
        self.invite_repo = invite_repo
        self.uow = uow
        self.clock = clock
        self.make_code = make_code

    async def execute(self, inviter: User, invited_name: str, is_admin: bool) -> Invite:
        await raise_if_name_taken(self.user_repo, invited_name)

        now = self.clock()
        invite = Invite(
            code=self.make_code(),
            invited_name=invited_name,
            inviter_id=inviter.id,
            is_admin=is_admin,
            expires_at=now + CODE_LIFETIME,
            created_at=now,
        )
        async with self.uow:
            await self.invite_repo.delete_unused_for_name(invited_name)
            created = await self.invite_repo.create(invite)
            await self.uow.commit()
        return created
