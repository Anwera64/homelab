from datetime import datetime
from typing import List, Protocol
from app.domain.entities.calendar_event import CalendarEvent
from app.domain.entities.integration_credential import CalendarCredential


class ICalendarConnector(Protocol):
    async def test_connection(self, credential: CalendarCredential, secret: str) -> bool:
        ...

    async def fetch_events(
        self,
        credential: CalendarCredential,
        secret: str,
        start_time: datetime,
        end_time: datetime,
        limit: int = 50,
        timeout: float = 10.0,
    ) -> List[CalendarEvent]:
        ...

    async def create_event(
        self,
        credential: CalendarCredential,
        secret: str,
        title: str,
        start_time: datetime,
        end_time: datetime,
        description: str = "",
        location: str = "",
        is_all_day: bool = False,
        timeout: float = 10.0,
    ) -> CalendarEvent:
        ...

    async def update_event(
        self,
        credential: CalendarCredential,
        secret: str,
        event_id: str,
        title: str | None = None,
        start_time: datetime | None = None,
        end_time: datetime | None = None,
        description: str | None = None,
        location: str | None = None,
        is_all_day: bool | None = None,
        timeout: float = 10.0,
    ) -> CalendarEvent:
        ...

    async def delete_event(
        self,
        credential: CalendarCredential,
        secret: str,
        event_id: str,
        timeout: float = 10.0,
    ) -> bool:
        ...
