from typing import List

from app.domain.entities.calendar_source import CalendarSource, DiscoveredCalendar
from app.domain.entities.integration_credential import CalendarCredential
from app.domain.repositories.calendar_connector import ICalendarConnector
from app.domain.repositories.calendar_source_repository import ICalendarSourceRepository

KEY_PREFIX = "c"


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
