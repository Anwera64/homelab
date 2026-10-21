import asyncio
from dataclasses import replace
from datetime import datetime
from typing import List, Optional
from app.domain.entities.calendar_event import CalendarEvent, CalendarReading
from app.domain.entities.calendar_source import CalendarSource
from app.domain.exceptions import CalendarIntegrationException, CalendarNotConnectedException
from app.domain.repositories.calendar_credential_repository import ICalendarCredentialRepository
from app.domain.repositories.calendar_connector import ICalendarConnector
from app.domain.use_cases.integrations.calendar_secret_resolver import CalendarSecretResolver
from app.domain.use_cases.integrations.calendar_sources import SyncCalendarSourcesUseCase


class GetCalendarEventsUseCase:
    def __init__(
        self,
        credential_repo: ICalendarCredentialRepository,
        connector: ICalendarConnector,
        secrets: CalendarSecretResolver,
        sync: Optional[SyncCalendarSourcesUseCase] = None,
    ):
        self.credential_repo = credential_repo
        self.connector = connector
        self.secrets = secrets
        # Without it, the connection's one calendar is read, as before calendars could be picked.
        self.sync = sync

    async def execute(
        self,
        user_id: str,
        start_time: datetime,
        end_time: datetime,
        limit: int = 50,
        timeout: float = 10.0,
    ) -> List[CalendarEvent]:
        return (await self.read(user_id, start_time, end_time, limit, timeout)).events

    async def read(
        self,
        user_id: str,
        start_time: datetime,
        end_time: datetime,
        limit: int = 50,
        timeout: float = 10.0,
    ) -> CalendarReading:
        """
        Every picked calendar, read side by side. One that can't be read now is named rather than
        failing the rest; only when none can be read is it a failure.
        """
        credential = await self.credential_repo.get_by_user_id(user_id)
        if not credential or not credential.is_active:
            raise CalendarNotConnectedException("No calendar configured for user.")

        secret = await self.secrets.resolve(credential)
        if self.sync is None:
            events = await self.connector.fetch_events(
                credential=credential, secret=secret, start_time=start_time, end_time=end_time, limit=limit, timeout=timeout
            )
            return CalendarReading(events=events)

        picked = [s for s in await self.sync.execute(credential, secret) if s.picked]

        async def read_one(source: CalendarSource) -> List[CalendarEvent]:
            events = await self.connector.fetch_events(
                credential=credential,
                secret=secret,
                start_time=start_time,
                end_time=end_time,
                limit=limit,
                timeout=timeout,
                calendar_url=source.url,
            )
            return [
                replace(e, calendar_key=source.key, calendar_name=source.name, read_only=not source.writable)
                for e in events
            ]

        results = await asyncio.gather(*(read_one(s) for s in picked), return_exceptions=True)
        failures = [(s, r) for s, r in zip(picked, results) if isinstance(r, BaseException)]
        if picked and len(failures) == len(picked):
            raise CalendarIntegrationException(f"None of the calendars could be read: {failures[0][1]}")

        events = sorted(
            (e for r in results if not isinstance(r, BaseException) for e in r), key=lambda e: e.start_time
        )
        return CalendarReading(events=events[:limit], unreadable=[s.name for s, _ in failures])
