from datetime import datetime
from typing import Optional, Protocol
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update, delete

from app.data.models.device_session_model import DeviceSessionModel


class IDeviceSessionDataSource(Protocol):
    async def create(self, device_session: DeviceSessionModel) -> DeviceSessionModel:
        ...

    async def get_by_id(self, device_session_id: str) -> Optional[DeviceSessionModel]:
        ...

    async def touch(self, device_session_id: str, seen_at: datetime) -> None:
        ...

    async def delete(self, device_session_id: str) -> None:
        ...

    async def delete_for_user(self, user_id: str, except_id: Optional[str] = None) -> None:
        ...

    async def delete_last_seen_before(self, cutoff: datetime) -> int:
        ...


class SqliteDeviceSessionDataSource(IDeviceSessionDataSource):
    def __init__(self, session: AsyncSession):
        self.session = session

    async def create(self, device_session: DeviceSessionModel) -> DeviceSessionModel:
        self.session.add(device_session)
        await self.session.flush()
        return device_session

    async def get_by_id(self, device_session_id: str) -> Optional[DeviceSessionModel]:
        stmt = select(DeviceSessionModel).where(DeviceSessionModel.id == device_session_id)
        res = await self.session.execute(stmt)
        return res.scalars().first()

    async def touch(self, device_session_id: str, seen_at: datetime) -> None:
        stmt = (
            update(DeviceSessionModel)
            .where(DeviceSessionModel.id == device_session_id)
            .values(last_seen_at=seen_at)
            .execution_options(synchronize_session="fetch")
        )
        await self.session.execute(stmt)
        await self.session.flush()

    async def delete(self, device_session_id: str) -> None:
        stmt = delete(DeviceSessionModel).where(DeviceSessionModel.id == device_session_id)
        await self.session.execute(stmt)
        await self.session.flush()

    async def delete_for_user(self, user_id: str, except_id: Optional[str] = None) -> None:
        stmt = delete(DeviceSessionModel).where(DeviceSessionModel.user_id == user_id)
        if except_id is not None:
            stmt = stmt.where(DeviceSessionModel.id != except_id)
        await self.session.execute(stmt)
        await self.session.flush()

    async def delete_last_seen_before(self, cutoff: datetime) -> int:
        stmt = delete(DeviceSessionModel).where(DeviceSessionModel.last_seen_at < cutoff)
        res = await self.session.execute(stmt)
        await self.session.flush()
        return res.rowcount or 0
