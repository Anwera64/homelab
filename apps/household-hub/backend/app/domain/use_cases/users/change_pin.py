from app.domain.exceptions import WrongConfirmationPinException, WrongPinException
from app.domain.repositories.security_service import IPasswordHasher, ITokenService
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.repositories.user_repository import IUserRepository
from app.domain.use_cases.auth.verify_member_pin import VerifyMemberPinUseCase


class ChangePinUseCase:
    """
    A signed-in member changes their PIN. The current one is checked under the same lockout as
    signing in. They might be changing it because someone saw it, so the token version moves on and
    every device already signed in has to sign in again — except this one, which gets a new token.
    """

    def __init__(
        self,
        verify_pin: VerifyMemberPinUseCase,
        user_repo: IUserRepository,
        hasher: IPasswordHasher,
        uow: IUnitOfWork,
        token_service: ITokenService,
    ):
        self.verify_pin = verify_pin
        self.user_repo = user_repo
        self.hasher = hasher
        self.uow = uow
        self.token_service = token_service

    async def execute(self, user_id: str, current_pin: str, new_pin: str) -> dict:
        try:
            user = await self.verify_pin.execute(user_id, current_pin)
        except WrongPinException as e:
            # Signed in already: a wrong PIN here must not read as a token the hub refused.
            raise WrongConfirmationPinException(e.attempts_left) from e

        user.hashed_pin = self.hasher.hash(new_pin)
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
