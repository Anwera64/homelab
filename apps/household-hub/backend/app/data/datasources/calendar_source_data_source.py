from typing import List

from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.data.models.calendar_source_model import CalendarSourceModel


class SqliteCalendarSourceDataSource:
    def __init__(self, session: AsyncSession):
        self.session = session

    async def list_for(self, credential_id: str) -> List[CalendarSourceModel]:
        stmt = (
            select(CalendarSourceModel)
            .where(CalendarSourceModel.credential_id == credential_id)
            .order_by(CalendarSourceModel.position)
        )
        return list((await self.session.execute(stmt)).scalars())

    async def replace_all(self, credential_id: str, models: List[CalendarSourceModel]) -> None:
        await self.session.execute(delete(CalendarSourceModel).where(CalendarSourceModel.credential_id == credential_id))
        self.session.add_all(models)
        await self.session.flush()
