from typing import Protocol, Optional
from app.domain.entities.integration_credential import CalendarCredential


class ICalendarCredentialRepository(Protocol):
    async def get_by_user_id(self, user_id: str) -> Optional[CalendarCredential]:
        ...

    async def save(self, credential: CalendarCredential) -> CalendarCredential:
        ...

    async def delete_by_user_id(self, user_id: str) -> bool:
        ...
