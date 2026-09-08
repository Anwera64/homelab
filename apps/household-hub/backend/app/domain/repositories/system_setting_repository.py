from typing import Protocol, Optional
from app.domain.entities.system_setting import SystemSetting


class ISystemSettingRepository(Protocol):
    async def get(self, key: str) -> Optional[SystemSetting]:
        ...

    async def set(self, key: str, value: str) -> SystemSetting:
        ...

    async def set_if_not_exists(self, key: str, value: str) -> bool:
        """
        Atomically set a setting key only if it does not already exist.
        Returns True if the key was set, False if it already existed.
        """
        ...

    async def delete(self, key: str) -> bool:
        """Delete a setting key. Returns True if deleted, False if not found."""
        ...
