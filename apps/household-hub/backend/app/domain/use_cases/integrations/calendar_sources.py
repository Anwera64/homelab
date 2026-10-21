from dataclasses import replace
from typing import List, Optional

from app.domain.entities.calendar_event import CalendarEvent
from app.domain.entities.calendar_source import CalendarSource, DiscoveredCalendar
from app.domain.entities.integration_credential import CalendarCredential
from app.domain.exceptions import CalendarChoiceException
from app.domain.repositories.calendar_connector import ICalendarConnector
from app.domain.repositories.calendar_source_repository import ICalendarSourceRepository

KEY_PREFIX = "c"

# What a calendar is chosen for: a new event, a change or removal of one, or only looking at it.
CREATE = "create"
CHANGE = "change"
LOOK = "look"


def _number(key: str) -> int:
    digits = key[len(KEY_PREFIX):]
    return int(digits) if digits.isdigit() else 0


def merge(stored: List[CalendarSource], account: List[DiscoveredCalendar]) -> List[CalendarSource]:
    """
    The account's calendars as the hub keeps them. A calendar already known keeps its key and the
    member's choice; a new one is read when the member can write to it, since a read-only one is
    mostly a subscription such as holidays. A key is never handed out twice.
    """
    known = {s.url: s for s in stored}
    next_number = max((_number(s.key) for s in stored), default=0) + 1
    merged: List[CalendarSource] = []
    for calendar in account:
        before = known.get(calendar.url)
        if before is not None:
            merged.append(
                CalendarSource(
                    key=before.key, url=calendar.url, name=calendar.name, writable=calendar.writable, picked=before.picked
                )
            )
            continue
        merged.append(
            CalendarSource(
                key=f"{KEY_PREFIX}{next_number}",
                url=calendar.url,
                name=calendar.name,
                writable=calendar.writable,
                picked=calendar.writable,
            )
        )
        next_number += 1
    return merged


class SyncCalendarSourcesUseCase:
    """Brings the member's kept calendars in line with what their account has now."""

    def __init__(self, source_repo: ICalendarSourceRepository, connector: ICalendarConnector):
        self.source_repo = source_repo
        self.connector = connector

    async def execute(self, credential: CalendarCredential, secret: str) -> List[CalendarSource]:
        account = await self.connector.list_calendars(credential, secret)
        synced = merge(await self.source_repo.list_for(credential.id), account)
        await self.source_repo.replace_all(credential.id, synced)
        return synced

    async def choose(
        self, credential: CalendarCredential, secret: str, key: Optional[str], purpose: str
    ) -> CalendarSource:
        return choose(await self.execute(credential, secret), key, purpose)


def _named(sources: List[CalendarSource]) -> str:
    return ", ".join(f"{s.name} ({s.key})" for s in sources) or "none"


def choose(sources: List[CalendarSource], key: Optional[str], purpose: str) -> CalendarSource:
    """
    The calendar a step is for. A new event with more than one calendar to write to needs the
    member's word on which: the model is told to ask, not to guess. A change names the calendar
    calendar_read gave, since the same event id can't be looked for everywhere.
    """
    picked = [s for s in sources if s.picked]
    writable = [s for s in picked if s.writable]
    if key:
        source = next((s for s in picked if s.key == key), None)
        if source is None:
            raise CalendarChoiceException(
                f"The member has no calendar '{key}' to use. Their calendars: {_named(picked)}."
            )
        if purpose != LOOK and not source.writable:
            raise CalendarChoiceException(
                f"{source.name} ({source.key}) is read-only: nothing on it can be added, changed or removed."
            )
        return source
    if purpose == CREATE:
        if len(writable) == 1:
            return writable[0]
        if not writable:
            raise CalendarChoiceException("The member has no calendar the hub may add events to.")
        raise CalendarChoiceException(
            f"The member has more than one calendar to add it to: {_named(writable)}. Ask the member "
            "which one, then pass its key as 'calendar'."
        )
    if len(picked) == 1:
        return picked[0]
    raise CalendarChoiceException(
        f"Say which calendar the event is on: pass 'calendar' as calendar_read gave it. "
        f"The member's calendars: {_named(picked)}."
    )


def on(event: Optional[CalendarEvent], source: CalendarSource) -> Optional[CalendarEvent]:
    """[event] as the calendar it is on: its key, its name, and whether it can be changed."""
    if event is None:
        return None
    return replace(event, calendar_key=source.key, calendar_name=source.name, read_only=not source.writable)
