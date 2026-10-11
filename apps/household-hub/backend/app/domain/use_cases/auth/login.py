from app.domain.use_cases.auth.sign_in_device import SignInDeviceUseCase
from app.domain.use_cases.auth.verify_member_pin import VerifyMemberPinUseCase


class LoginUseCase:
    """A member picked from the profile list signs in with their PIN."""

    def __init__(self, verify_pin: VerifyMemberPinUseCase, sign_in_device: SignInDeviceUseCase):
        self.verify_pin = verify_pin
        self.sign_in_device = sign_in_device

    async def execute(self, user_id: str, pin: str) -> dict:
        user = await self.verify_pin.execute(user_id, pin)
        return await self.sign_in_device.execute(user)
