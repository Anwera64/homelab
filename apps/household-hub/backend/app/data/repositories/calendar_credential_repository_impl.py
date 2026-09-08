from typing import Optional
from app.domain.entities.integration_credential import CalendarCredential
from app.domain.repositories.calendar_credential_repository import ICalendarCredentialRepository
from app.data.datasources.calendar_credential_data_source import SqliteCalendarCredentialDataSource
from app.data.mappers.calendar_credential_data_mapper import CalendarCredentialDataMapper


class CalendarCredentialRepositoryImpl(ICalendarCredentialRepository):
    def __init__(self, data_source: SqliteCalendarCredentialDataSource, mapper: CalendarCredentialDataMapper):
        self.data_source = data_source
        self.mapper = mapper

    async def get_by_user_id(self, user_id: str) -> Optional[CalendarCredential]:
        model = await self.data_source.get_by_user_id(user_id)
        if not model:
            return None
        return self.mapper.to_domain(model)

    async def save(self, credential: CalendarCredential) -> CalendarCredential:
        model = self.mapper.to_model(credential)
        saved_model = await self.data_source.save(model)
        return self.mapper.to_domain(saved_model)

    async def delete_by_user_id(self, user_id: str) -> bool:
        return await self.data_source.delete_by_user_id(user_id)
