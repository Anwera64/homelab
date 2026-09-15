from app.domain.entities.user import User
from app.domain.repositories.security_service import ITokenService


class RefreshTokenUseCase:
    """
    Re-issues a signed-in member's token with a fresh expiry, so a phone in use never reaches the
    end of its 30 days. The caller has already been authenticated: an expired token, one from an
    older token version, or an inactive member never gets this far.
    """

    def __init__(self, token_service: ITokenService):
        self.token_service = token_service

    async def execute(self, user: User) -> dict:
        token = self.token_service.create_access_token(
            subject=user.id, is_admin=user.is_admin, token_version=user.token_version
        )
        return {
            "access_token": token,
            "token_type": "bearer",
            "user": user,
        }
