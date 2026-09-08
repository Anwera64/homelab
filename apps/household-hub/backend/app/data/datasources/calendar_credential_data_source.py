from typing import Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, delete
from app.data.models.calendar_credential_model import CalendarCredentialModel


class SqliteCalendarCredentialDataSource:
    def __init__(self, session: AsyncSession):
        self.session = session

    async def get_by_user_id(self, user_id: str) -> Optional[CalendarCredentialModel]:
        stmt = select(CalendarCredentialModel).where(CalendarCredentialModel.user_id == user_id)
        result = await self.session.execute(stmt)
        return result.scalar_one_or_none()

    async def save(self, model: CalendarCredentialModel) -> CalendarCredentialModel:
        existing = await self.get_by_user_id(model.user_id)
        if existing:
            existing.provider = model.provider
            existing.url = model.url
            existing.username = model.username
            existing.encrypted_secret = model.encrypted_secret
            existing.calendar_name = model.calendar_name
            existing.is_active = model.is_active
            existing.updated_at = model.updated_at
            return existing
        else:
            self.session.add(model)
            await self.session.flush()
            return model

    async def delete_by_user_id(self, user_id: str) -> bool:
        stmt = delete(CalendarCredentialModel).where(CalendarCredentialModel.user_id == user_id)
        result = await self.session.execute(stmt)
        return result.rowcount > 0
