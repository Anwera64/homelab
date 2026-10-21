from datetime import datetime
from typing import Optional
from app.domain.entities.calendar_event import CalendarEvent
from app.domain.repositories.calendar_credential_repository import ICalendarCredentialRepository
from app.domain.repositories.calendar_connector import ICalendarConnector
from app.domain.use_cases.integrations.calendar_secret_resolver import CalendarSecretResolver
from app.domain.use_cases.integrations.calendar_sources import CHANGE, CREATE, LOOK, SyncCalendarSourcesUseCase, on


class GetCalendarEventUseCase:
    """
    One event by its ID, so a remove or change card can name what it is about (#63). None when the
    member has no calendar or the calendar has no such event; a calendar that fails still raises.
    """

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
        event_id: str,
        occurrence_start: Optional[datetime] = None,
        timeout: float = 5.0,
        calendar: Optional[str] = None,
    ) -> Optional[CalendarEvent]:
        """[occurrence_start] picks one date of a repeating event; without it, it is the series."""
        credential = await self.credential_repo.get_by_user_id(user_id)
        if not credential or not credential.is_active:
            return None

        secret = await self.secrets.resolve(credential)
        source = None if self.sync is None else await self.sync.choose(credential, secret, calendar, LOOK)
        event = await self.connector.get_event(
            credential=credential,
            secret=secret,
            event_id=event_id,
            occurrence_start=occurrence_start,
            timeout=timeout,
            calendar_url=source.url if source else None,
        )
        return on(event, source) if source else event
