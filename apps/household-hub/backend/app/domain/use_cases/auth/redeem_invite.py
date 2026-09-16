from datetime import datetime, timezone
from typing import Callable, Optional

from app.domain.exceptions import InviteInvalidException
from app.domain.repositories.invite_repository import IInviteRepository
from app.domain.repositories.security_service import ITokenService
from app.domain.repositories.user_repository import IUserRepository
from app.domain.use_cases.auth.guard_code_guesses import GuardCodeGuessesUseCase
from app.domain.use_cases.auth.look_up_invite import usable_invite
from app.domain.use_cases.users.create_member import CreateMemberUseCase
from app.domain.use_cases.users.member_names import raise_if_name_taken


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


class RedeemInviteUseCase:
    """
    The joiner redeems their code with the name, PIN and colour they chose, and is signed in.
    Public, so two rules hold: whether they join as an admin comes from the stored invite, never
    from what they send; and a code works once, claimed in a single conditional write.
    """

    def __init__(
        self,
        invite_repo: IInviteRepository,
        user_repo: IUserRepository,
        create_member: CreateMemberUseCase,
        token_service: ITokenService,
        guard: GuardCodeGuessesUseCase,
        clock: Callable[[], datetime] = _utc_now,
    ):
        self.invite_repo = invite_repo
        self.user_repo = user_repo
        self.create_member = create_member
        self.token_service = token_service
        self.guard = guard
        self.clock = clock

    async def execute(self, code: str, full_name: str, pin: str, avatar_color: Optional[str]) -> dict:
        return await self.guard.execute(lambda: self._redeem(code, full_name, pin, avatar_color))

    async def _redeem(self, code: str, full_name: str, pin: str, avatar_color: Optional[str]) -> dict:
        now = self.clock()
        invite = await usable_invite(self.invite_repo, code, now)
        # Before claiming, so a name someone took meanwhile doesn't use up the code.
        await raise_if_name_taken(self.user_repo, full_name)
        if not await self.invite_repo.claim(invite.id, now):
            raise InviteInvalidException("That invite code isn't valid.")

        member = await self.create_member.execute(
            full_name=full_name,
            pin=pin,
            avatar_color=avatar_color,
            is_admin=invite.is_admin,
        )
        token = self.token_service.create_access_token(
            subject=member.id, is_admin=member.is_admin, token_version=member.token_version
        )
        return {
            "access_token": token,
            "token_type": "bearer",
            "user": member,
        }
