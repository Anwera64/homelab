from datetime import datetime
from typing import Optional
from app.domain.entities.calendar_event import CalendarEvent, Repeat
from app.domain.exceptions import CalendarNotConnectedException
from app.domain.repositories.calendar_credential_repository import ICalendarCredentialRepository
from app.domain.repositories.calendar_connector import ICalendarConnector
from app.domain.use_cases.integrations.calendar_secret_resolver import CalendarSecretResolver
from app.domain.use_cases.integrations.calendar_sources import CHANGE, CREATE, LOOK, SyncCalendarSourcesUseCase, on


class CreateCalendarEventUseCase:
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
        # Without it, the connection's one calendar is used, as before calendars could be picked.
        self.sync = sync

    async def execute(
        self,
        user_id: str,
        title: str,
        start_time: datetime,
        end_time: datetime,
        description: str = "",
        location: str = "",
        is_all_day: bool = False,
        timeout: float = 10.0,
        repeat: Optional[Repeat] = None,
        calendar: Optional[str] = None,
    ) -> CalendarEvent:
        credential = await self.credential_repo.get_by_user_id(user_id)
        if not credential or not credential.is_active:
            raise CalendarNotConnectedException("No calendar configured for user.")

        secret = await self.secrets.resolve(credential)
        source = None if self.sync is None else await self.sync.choose(credential, secret, calendar, CREATE)
        created = await self.connector.create_event(
            credential=credential,
            secret=secret,
            title=title,
            start_time=start_time,
            end_time=end_time,
            description=description,
            location=location,
            is_all_day=is_all_day,
            timeout=timeout,
            repeat=repeat,
            calendar_url=source.url if source else None,
        )
        return on(created, source) if source else created
