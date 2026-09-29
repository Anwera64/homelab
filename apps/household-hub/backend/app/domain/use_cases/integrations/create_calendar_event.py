from datetime import datetime
from typing import Optional
from app.domain.entities.calendar_event import CalendarEvent, Repeat
from app.domain.exceptions import CalendarNotConnectedException
from app.domain.repositories.calendar_credential_repository import ICalendarCredentialRepository
from app.domain.repositories.calendar_connector import ICalendarConnector
from app.domain.use_cases.integrations.calendar_secret_resolver import CalendarSecretResolver


class CreateCalendarEventUseCase:
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
        title: str,
        start_time: datetime,
        end_time: datetime,
        description: str = "",
        location: str = "",
        is_all_day: bool = False,
        timeout: float = 10.0,
        repeat: Optional[Repeat] = None,
    ) -> CalendarEvent:
        credential = await self.credential_repo.get_by_user_id(user_id)
        if not credential or not credential.is_active:
            raise CalendarNotConnectedException("No calendar configured for user.")

        secret = await self.secrets.resolve(credential)
        return await self.connector.create_event(
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
        )
