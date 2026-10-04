from datetime import datetime
from typing import List, Optional, Protocol
from app.domain.entities.calendar_event import CalendarEvent, Repeat
from app.domain.entities.integration_credential import CalendarCredential


class ICalendarConnector(Protocol):
    async def test_connection(self, credential: CalendarCredential, secret: str) -> bool:
        """
        True when the account answers. Raises CalendarAuthException when the server refuses the
        credentials, and CalendarUnreachableException when it can't be reached.
        """
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
        repeat: Optional[Repeat] = None,
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
        repeat: Optional[Repeat] = None,
        occurrence_start: datetime | None = None,
        scope: str | None = None,
    ) -> CalendarEvent:
        """
        On a repeating event, [occurrence_start] names the date meant and [scope] whether the change
        is for that date only or it and every later one; [repeat] then sets how the latter repeat.
        """
        ...

    async def get_event(
        self,
        credential: CalendarCredential,
        secret: str,
        event_id: str,
        timeout: float = 10.0,
    ) -> Optional[CalendarEvent]:
        """The event with that ID, or None when the calendar has none."""
        ...

    async def delete_event(
        self,
        credential: CalendarCredential,
        secret: str,
        event_id: str,
        timeout: float = 10.0,
        occurrence_start: datetime | None = None,
        scope: str | None = None,
    ) -> bool:
        """On a repeating event, removes the date [occurrence_start] only, or it and every later one."""
        ...
