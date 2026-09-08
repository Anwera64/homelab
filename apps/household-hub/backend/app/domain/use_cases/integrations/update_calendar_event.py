from datetime import datetime
from typing import Optional
from app.domain.entities.calendar_event import CalendarEvent
from app.domain.exceptions import CalendarIntegrationException
from app.domain.repositories.calendar_credential_repository import ICalendarCredentialRepository
from app.domain.repositories.calendar_connector import ICalendarConnector
from app.domain.repositories.secret_cipher import ISecretCipher


class UpdateCalendarEventUseCase:
    def __init__(
        self,
        credential_repo: ICalendarCredentialRepository,
        connector: ICalendarConnector,
        cipher: ISecretCipher,
    ):
        self.credential_repo = credential_repo
        self.connector = connector
        self.cipher = cipher

    async def execute(
        self,
        user_id: str,
        event_id: str,
        title: Optional[str] = None,
        start_time: Optional[datetime] = None,
        end_time: Optional[datetime] = None,
        description: Optional[str] = None,
        location: Optional[str] = None,
        is_all_day: Optional[bool] = None,
        timeout: float = 10.0,
    ) -> CalendarEvent:
        credential = await self.credential_repo.get_by_user_id(user_id)
        if not credential or not credential.is_active:
            raise CalendarIntegrationException("No calendar configured for user.")

        secret = self.cipher.decrypt(credential.encrypted_secret)
        return await self.connector.update_event(
            credential=credential,
            secret=secret,
            event_id=event_id,
            title=title,
            start_time=start_time,
            end_time=end_time,
            description=description,
            location=location,
            is_all_day=is_all_day,
            timeout=timeout,
        )
