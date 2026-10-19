import logging
from datetime import datetime
from typing import Optional

from app.domain.exceptions import CalendarNotConnectedException, ToolPermissionDeniedException
from app.domain.repositories.calendar_credential_repository import ICalendarCredentialRepository
from app.domain.repositories.calendar_connector import ICalendarConnector
from app.domain.use_cases.integrations.calendar_secret_resolver import CalendarSecretResolver
from app.domain.use_cases.memories.follow_event import FollowEventChangeUseCase

logger = logging.getLogger(__name__)


class DeleteCalendarEventUseCase:
    def __init__(
        self,
        credential_repo: ICalendarCredentialRepository,
        connector: ICalendarConnector,
        secrets: CalendarSecretResolver,
        allow_agent_delete: bool = True,
        follow: Optional[FollowEventChangeUseCase] = None,
    ):
        self.credential_repo = credential_repo
        self.connector = connector
        self.secrets = secrets
        self.allow_agent_delete = allow_agent_delete
        # Keeps the member's memories of the event true once the calendar has answered (memory spec §7).
        self.follow = follow

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
            raise CalendarNotConnectedException("No calendar configured for user.")

        secret = await self.secrets.resolve(credential)
        deleted = await self.connector.delete_event(
            credential=credential,
            secret=secret,
            event_id=event_id,
            timeout=timeout,
            occurrence_start=occurrence_start,
            scope=scope,
        )
        await self._follow(user_id, event_id)
        return deleted

    async def _follow(self, user_id: str, event_id: str) -> None:
        if self.follow is None:
            return
        try:
            await self.follow.execute(user_id, event_id)
        except Exception as exc:
            # The calendar change stands; the periodic check brings the memories in line later.
            logger.warning("Calendar link | member=%s event=%s memories could not follow: %s", user_id, event_id, exc)
