from datetime import datetime, timezone
from typing import Callable

from app.domain.entities.invite import Invite, InvitePreview
from app.domain.entities.one_time_code import normalise_code
from app.domain.entities.user import DEFAULT_AVATAR_COLOR
from app.domain.exceptions import InviteInvalidException
from app.domain.repositories.invite_repository import IInviteRepository
from app.domain.repositories.user_repository import IUserRepository
from app.domain.use_cases.auth.guard_code_guesses import GuardCodeGuessesUseCase


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


async def usable_invite(invite_repo: IInviteRepository, code: str, now: datetime) -> Invite:
    """The invite behind a code, if it can still be used. Unknown, used and expired answer alike."""
    invite = await invite_repo.get_by_code(normalise_code(code))
    if not invite or not invite.is_usable_at(now):
        raise InviteInvalidException("That invite code isn't valid.")
    return invite


class LookUpInviteUseCase:
    """
    Before choosing a PIN, the joiner sees who invited them and the name they were invited as.
    Public, so every look-up counts as a guess.
    """

    def __init__(
        self,
        invite_repo: IInviteRepository,
        user_repo: IUserRepository,
        guard: GuardCodeGuessesUseCase,
        clock: Callable[[], datetime] = _utc_now,
    ):
        self.invite_repo = invite_repo
        self.user_repo = user_repo
        self.guard = guard
        self.clock = clock

    async def execute(self, code: str) -> InvitePreview:
        return await self.guard.execute(lambda: self._look_up(code))

    async def _look_up(self, code: str) -> InvitePreview:
        invite = await usable_invite(self.invite_repo, code, self.clock())
        inviter = await self.user_repo.get_by_id(invite.inviter_id) if invite.inviter_id else None
        return InvitePreview(
            invited_name=invite.invited_name,
            inviter_name=inviter.full_name if inviter else "",
            inviter_avatar_color=inviter.avatar_color if inviter else DEFAULT_AVATAR_COLOR,
        )
