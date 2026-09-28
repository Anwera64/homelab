from datetime import datetime
from typing import Optional

from app.domain.exceptions import CalendarIntegrationException, ToolPermissionDeniedException
from app.domain.repositories.calendar_credential_repository import ICalendarCredentialRepository
from app.domain.repositories.calendar_connector import ICalendarConnector
from app.domain.use_cases.integrations.calendar_secret_resolver import CalendarSecretResolver


class DeleteCalendarEventUseCase:
    def __init__(
        self,
        credential_repo: ICalendarCredentialRepository,
        connector: ICalendarConnector,
        secrets: CalendarSecretResolver,
        allow_agent_delete: bool = True,
    ):
        self.credential_repo = credential_repo
        self.connector = connector
        self.secrets = secrets
        self.allow_agent_delete = allow_agent_delete

    async def execute(
        self,
        user_id: str,
        event_id: str,
        timeout: float = 10.0,
        occurrence_start: Optional[datetime] = None,
        scope: Optional[str] = None,
    ) -> bool:
        if not self.allow_agent_delete:
            raise ToolPermissionDeniedException(
                "Calendar event deletion is restricted by safety policy."
            )

        credential = await self.credential_repo.get_by_user_id(user_id)
        if not credential or not credential.is_active:
            raise CalendarIntegrationException("No calendar configured for user.")

        secret = await self.secrets.resolve(credential)
        return await self.connector.delete_event(
            credential=credential,
            secret=secret,
            event_id=event_id,
            timeout=timeout,
            occurrence_start=occurrence_start,
            scope=scope,
        )
