from typing import List, Protocol

from app.domain.entities.calendar_source import CalendarSource


class ICalendarSourceRepository(Protocol):
    async def list_for(self, credential_id: str) -> List[CalendarSource]:
        ...

    async def replace_all(self, credential_id: str, sources: List[CalendarSource]) -> None:
        ...
