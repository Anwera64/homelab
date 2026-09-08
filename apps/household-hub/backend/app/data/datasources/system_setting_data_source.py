from datetime import datetime, timezone
from typing import Protocol, Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, delete
from sqlalchemy.dialects.sqlite import insert as sqlite_insert

from app.data.models.system_setting_model import SystemSettingModel


class ISystemSettingDataSource(Protocol):
    async def get(self, key: str) -> Optional[SystemSettingModel]:
        ...

    async def set(self, key: str, value: str) -> SystemSettingModel:
        ...

    async def set_if_not_exists(self, key: str, value: str) -> bool:
        ...

    async def delete(self, key: str) -> bool:
        ...


class SqliteSystemSettingDataSource(ISystemSettingDataSource):
    def __init__(self, session: AsyncSession):
        self.session = session

    async def get(self, key: str) -> Optional[SystemSettingModel]:
        stmt = select(SystemSettingModel).where(SystemSettingModel.key == key)
        res = await self.session.execute(stmt)
        return res.scalars().first()

    async def set(self, key: str, value: str) -> SystemSettingModel:
        now = datetime.now(timezone.utc)
        stmt = sqlite_insert(SystemSettingModel).values(key=key, value=value, created_at=now, updated_at=now)
        stmt = stmt.on_conflict_do_update(
            index_elements=["key"],
            set_={"value": value, "updated_at": now},
        )
        await self.session.execute(stmt)
        await self.session.flush()
        return await self.get(key)

    async def set_if_not_exists(self, key: str, value: str) -> bool:
        now = datetime.now(timezone.utc)
        stmt = (
            sqlite_insert(SystemSettingModel)
            .values(key=key, value=value, created_at=now, updated_at=now)
            .on_conflict_do_nothing(index_elements=["key"])
        )
        res = await self.session.execute(stmt)
        await self.session.commit()
        return res.rowcount > 0

    async def delete(self, key: str) -> bool:
        stmt = delete(SystemSettingModel).where(SystemSettingModel.key == key)
        res = await self.session.execute(stmt)
        await self.session.commit()
        return res.rowcount > 0
