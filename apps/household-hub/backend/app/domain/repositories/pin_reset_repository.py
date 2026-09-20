from datetime import datetime
from typing import Optional, Protocol

from app.domain.entities.pin_reset import PinReset


class IPinResetRepository(Protocol):
    async def create(self, reset: PinReset) -> PinReset:
        ...

    async def get_by_code(self, code: str) -> Optional[PinReset]:
        ...

    async def delete_unused_for_target(self, target_user_id: str) -> None:
        """Retires every unused reset code for that member."""
        ...

    async def claim(self, reset_id: str, used_at: datetime) -> bool:
        """
        Marks the code used only if nobody has yet, in one conditional write.
        True if this call claimed it; False if it was already used.
        """
        ...
