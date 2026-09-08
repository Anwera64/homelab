from app.domain.entities.user import User
from app.domain.repositories.user_repository import IUserRepository
from app.domain.repositories.security_service import IPasswordHasher, ITokenService
from app.domain.exceptions import InvalidOperationException, AuthenticationException


class LoginUseCase:
    def __init__(
        self,
        user_repo: IUserRepository,
        hasher: IPasswordHasher,
        token_service: ITokenService,
        dummy_hash: str,
    ):
        self.user_repo = user_repo
        self.hasher = hasher
        self.token_service = token_service
        self.dummy_hash = dummy_hash

    async def execute(self, username: str, password: str) -> dict:
        user = await self.user_repo.get_by_username(username)
        if not user:
            # Timing attack defense: execute constant-time hash verification against dummy hash
            self.hasher.verify(password, self.dummy_hash)
            raise AuthenticationException("Invalid username or password")

        if not self.hasher.verify(password, user.hashed_password):
            raise AuthenticationException("Invalid username or password")

        if not user.is_active:
            raise InvalidOperationException("User account is inactive")

        token = self.token_service.create_access_token(subject=user.id, is_admin=user.is_admin)
        return {
            "access_token": token,
            "token_type": "bearer",
            "user": user,
        }
