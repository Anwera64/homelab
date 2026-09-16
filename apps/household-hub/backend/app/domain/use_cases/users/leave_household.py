from app.domain.entities.user import User
from app.domain.exceptions import WrongConfirmationPinException, WrongPinException
from app.domain.repositories.user_repository import IUserRepository
from app.domain.use_cases.auth.verify_member_pin import VerifyMemberPinUseCase
from app.domain.use_cases.users.deactivate_member import DeactivateMemberUseCase


class LeaveHouseholdUseCase:
    """
    You leave the household yourself, confirming with your PIN — friction proportional to the damage,
    as typing someone's name is when you remove them. The only admin can't: there is no promoting
    anyone, so the household would be left without one.
    """

    def __init__(
        self,
        user_repo: IUserRepository,
        verify_pin: VerifyMemberPinUseCase,
        deactivate_member: DeactivateMemberUseCase,
    ):
        self.user_repo = user_repo
        self.verify_pin = verify_pin
        self.deactivate_member = deactivate_member

    async def execute(self, member: User, pin: str) -> None:
        try:
            await self.verify_pin.execute(member.id, pin)
        except WrongPinException as e:
            raise WrongConfirmationPinException(e.attempts_left) from e

        # The agents they made need a living owner; for a leaving admin that is the other admin.
        inheriting_admin = await self.user_repo.get_other_admin(exclude_user_id=member.id)
        await self.deactivate_member.remove(member, inheriting_admin=inheriting_admin)
