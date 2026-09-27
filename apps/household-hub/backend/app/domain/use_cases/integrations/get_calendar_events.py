from datetime import datetime
from typing import List
from app.domain.entities.calendar_event import CalendarEvent
from app.domain.exceptions import CalendarIntegrationException
from app.domain.repositories.calendar_credential_repository import ICalendarCredentialRepository
from app.domain.repositories.calendar_connector import ICalendarConnector
from app.domain.use_cases.integrations.calendar_secret_resolver import CalendarSecretResolver


class GetCalendarEventsUseCase:
    def __init__(
        self,
        credential_repo: ICalendarCredentialRepository,
        connector: ICalendarConnector,
        secrets: CalendarSecretResolver,
    ):
        self.credential_repo = credential_repo
        self.connector = connector
        self.secrets = secrets

    async def execute(
        self,
        user_id: str,
        start_time: datetime,
        end_time: datetime,
        limit: int = 50,
        timeout: float = 10.0,
    ) -> List[CalendarEvent]:
        credential = await self.credential_repo.get_by_user_id(user_id)
        if not credential or not credential.is_active:
            raise CalendarIntegrationException("No calendar configured for user.")

        secret = await self.secrets.resolve(credential)
        return await self.connector.fetch_events(
            credential=credential,
            secret=secret,
            start_time=start_time,
            end_time=end_time,
            limit=limit,
            timeout=timeout,
        )
