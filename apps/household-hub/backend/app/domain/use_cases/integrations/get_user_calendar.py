from typing import Optional
from app.domain.entities.integration_credential import CalendarCredential
from app.domain.repositories.calendar_credential_repository import ICalendarCredentialRepository


class GetUserCalendarUseCase:
    def __init__(self, credential_repo: ICalendarCredentialRepository):
        self.credential_repo = credential_repo

    async def execute(self, user_id: str) -> Optional[CalendarCredential]:
        return await self.credential_repo.get_by_user_id(user_id)
