from app.domain.repositories.security_service import ITokenService
from app.domain.use_cases.auth.verify_member_pin import VerifyMemberPinUseCase


class LoginUseCase:
    """A member picked from the profile list signs in with their PIN."""

    def __init__(self, verify_pin: VerifyMemberPinUseCase, token_service: ITokenService):
        self.verify_pin = verify_pin
        self.token_service = token_service

    async def execute(self, user_id: str, pin: str) -> dict:
        user = await self.verify_pin.execute(user_id, pin)
        token = self.token_service.create_access_token(
            subject=user.id, is_admin=user.is_admin, token_version=user.token_version
        )
        return {
            "access_token": token,
            "token_type": "bearer",
            "user": user,
        }
