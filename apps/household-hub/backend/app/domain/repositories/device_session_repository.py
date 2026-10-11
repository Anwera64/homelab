from datetime import datetime
from typing import Optional, Protocol

from app.domain.entities.device_session import DeviceSession


class IDeviceSessionRepository(Protocol):
    async def create(self, device_session: DeviceSession) -> DeviceSession:
        ...

    async def get_by_id(self, device_session_id: str) -> Optional[DeviceSession]:
        ...

    async def touch(self, device_session_id: str, seen_at: datetime) -> None:
        """Records that the device was just given a new token."""
        ...

    async def delete(self, device_session_id: str) -> None:
        ...

    async def delete_for_user(self, user_id: str, except_id: Optional[str] = None) -> None:
        """Signs the member out everywhere, or everywhere but the device named."""
        ...

    async def delete_last_seen_before(self, cutoff: datetime) -> int:
        """Removes the devices whose newest token is older than `cutoff`. Returns how many."""
        ...
