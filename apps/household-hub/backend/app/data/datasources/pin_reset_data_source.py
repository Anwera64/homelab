from datetime import datetime
from typing import Optional, Protocol
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update, delete

from app.data.models.pin_reset_model import PinResetModel


class IPinResetDataSource(Protocol):
    async def create(self, reset: PinResetModel) -> PinResetModel:
        ...

    async def get_by_code(self, code: str) -> Optional[PinResetModel]:
        ...

    async def delete_unused_for_target(self, target_user_id: str) -> None:
        ...

    async def claim(self, reset_id: str, used_at: datetime) -> bool:
        ...


class SqlitePinResetDataSource(IPinResetDataSource):
    def __init__(self, session: AsyncSession):
        self.session = session

    async def create(self, reset: PinResetModel) -> PinResetModel:
        self.session.add(reset)
        await self.session.flush()
        return reset

    async def get_by_code(self, code: str) -> Optional[PinResetModel]:
        stmt = select(PinResetModel).where(PinResetModel.code == code)
        res = await self.session.execute(stmt)
        return res.scalars().first()

    async def delete_unused_for_target(self, target_user_id: str) -> None:
        stmt = delete(PinResetModel).where(
            PinResetModel.target_user_id == target_user_id,
            PinResetModel.used_at.is_(None),
        )
        await self.session.execute(stmt)
        await self.session.flush()

    async def claim(self, reset_id: str, used_at: datetime) -> bool:
        stmt = (
            update(PinResetModel)
            .where(PinResetModel.id == reset_id, PinResetModel.used_at.is_(None))
            .values(used_at=used_at)
        )
        res = await self.session.execute(stmt)
        await self.session.flush()
        return (res.rowcount or 0) > 0
