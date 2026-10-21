from typing import List

from app.data.datasources.calendar_source_data_source import SqliteCalendarSourceDataSource
from app.data.mappers.calendar_source_data_mapper import CalendarSourceDataMapper
from app.domain.entities.calendar_source import CalendarSource
from app.domain.repositories.calendar_source_repository import ICalendarSourceRepository


class CalendarSourceRepositoryImpl(ICalendarSourceRepository):
    def __init__(self, data_source: SqliteCalendarSourceDataSource, mapper: CalendarSourceDataMapper):
        self.data_source = data_source
        self.mapper = mapper

    async def list_for(self, credential_id: str) -> List[CalendarSource]:
        return [self.mapper.to_domain(m) for m in await self.data_source.list_for(credential_id)]

    async def replace_all(self, credential_id: str, sources: List[CalendarSource]) -> None:
        await self.data_source.replace_all(
            credential_id, [self.mapper.to_model(s, credential_id, i) for i, s in enumerate(sources)]
        )
