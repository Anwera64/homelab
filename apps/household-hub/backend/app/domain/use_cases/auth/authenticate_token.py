from app.domain.entities.user import User
from app.domain.repositories.user_repository import IUserRepository
from app.domain.repositories.security_service import ITokenService
from app.domain.exceptions import InvalidOperationException, EntityNotFoundException


class AuthenticateTokenUseCase:
    def __init__(self, user_repo: IUserRepository, token_service: ITokenService):
        self.user_repo = user_repo
        self.token_service = token_service

    async def execute(self, token: str) -> User:
        payload = self.token_service.decode_token(token)
        user_id = payload.get("sub")
        if not user_id:
            raise InvalidOperationException("Invalid authentication token")

        user = await self.user_repo.get_by_id(user_id)
        if not user:
            raise EntityNotFoundException("User not found")
        if not user.is_active:
            raise InvalidOperationException("Inactive user")

        return user
