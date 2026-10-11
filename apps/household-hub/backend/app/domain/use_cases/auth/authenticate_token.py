from dataclasses import dataclass
from typing import Optional

from app.domain.entities.user import User
from app.domain.repositories.device_session_repository import IDeviceSessionRepository
from app.domain.repositories.user_repository import IUserRepository
from app.domain.repositories.security_service import ITokenService
from app.domain.exceptions import AuthenticationException, InvalidOperationException, EntityNotFoundException


@dataclass
class SignedInMember:
    """Who a token belongs to, and the device it was given to — none for a token from before #94."""
    user: User
    device_session_id: Optional[str] = None


class AuthenticateTokenUseCase:
    def __init__(
        self,
        user_repo: IUserRepository,
        token_service: ITokenService,
        device_session_repo: IDeviceSessionRepository,
    ):
        self.user_repo = user_repo
        self.token_service = token_service
        self.device_session_repo = device_session_repo

    async def execute(self, token: str) -> User:
        return (await self.authenticate(token)).user

    async def authenticate(self, token: str) -> SignedInMember:
        payload = self.token_service.decode_token(token)
        user_id = payload.get("sub")
        if not user_id:
            raise InvalidOperationException("Invalid authentication token")

        user = await self.user_repo.get_by_id(user_id)
        if not user:
            raise EntityNotFoundException("User not found")
        if not user.is_active:
            raise InvalidOperationException("Inactive user")
        # A token issued before the member's PIN changed, or before they were removed.
        if payload.get("ver") != user.token_version:
            raise AuthenticationException("Token no longer accepted")

        # A token that names a device is good only while that device is signed in. One that names
        # none was issued before devices were tracked, and lasts until it is refreshed or expires.
        device_session_id = payload.get("sid")
        if device_session_id:
            device = await self.device_session_repo.get_by_id(device_session_id)
            if not device or device.user_id != user.id:
                raise AuthenticationException("This device has signed out")

        return SignedInMember(user=user, device_session_id=device_session_id)
