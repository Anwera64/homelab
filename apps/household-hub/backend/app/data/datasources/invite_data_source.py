from datetime import datetime
from typing import Optional, Protocol
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update, delete, func

from app.data.models.invite_model import InviteModel


class IInviteDataSource(Protocol):
    async def create(self, invite: InviteModel) -> InviteModel:
        ...

    async def get_by_code(self, code: str) -> Optional[InviteModel]:
        ...

    async def delete_unused_for_name(self, invited_name: str) -> None:
        ...

    async def claim(self, invite_id: str, used_at: datetime) -> bool:
        ...


class SqliteInviteDataSource(IInviteDataSource):
    def __init__(self, session: AsyncSession):
        self.session = session

    async def create(self, invite: InviteModel) -> InviteModel:
        self.session.add(invite)
        await self.session.flush()
        return invite

    async def get_by_code(self, code: str) -> Optional[InviteModel]:
        stmt = select(InviteModel).where(InviteModel.code == code)
        res = await self.session.execute(stmt)
        return res.scalars().first()

    async def delete_unused_for_name(self, invited_name: str) -> None:
        stmt = delete(InviteModel).where(
            func.lower(InviteModel.invited_name) == invited_name.lower(),
            InviteModel.used_at.is_(None),
        )
        await self.session.execute(stmt)
        await self.session.flush()

    async def claim(self, invite_id: str, used_at: datetime) -> bool:
        stmt = (
            update(InviteModel)
            .where(InviteModel.id == invite_id, InviteModel.used_at.is_(None))
            .values(used_at=used_at)
        )
        res = await self.session.execute(stmt)
        await self.session.flush()
        return (res.rowcount or 0) > 0
