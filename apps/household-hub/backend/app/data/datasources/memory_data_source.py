from typing import Protocol, List, Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update, delete

from app.data.models.memory_model import MemoryModel


class IMemoryDataSource(Protocol):
    async def list_user_memories(
        self,
        user_id: str,
        scope: Optional[str] = None,
        agent_id: Optional[str] = None,
        category: Optional[str] = None,
        active_only: bool = True,
    ) -> List[MemoryModel]:
        ...

    async def list_household_memories(
        self,
        category: Optional[str] = None,
    ) -> List[MemoryModel]:
        ...

    async def get_by_id(self, memory_id: str) -> Optional[MemoryModel]:
        ...

    async def create(self, memory: MemoryModel) -> MemoryModel:
        ...

    async def update(self, memory: MemoryModel) -> MemoryModel:
        ...

    async def delete(self, memory_id: str) -> None:
        ...

    async def delete_personal_memories(self, user_id: str) -> None:
        ...

    async def reassign_household_memories(self, from_user_id: str, to_user_id: str) -> None:
        ...


class SqliteMemoryDataSource(IMemoryDataSource):
    def __init__(self, session: AsyncSession):
        self.session = session

    async def list_user_memories(
        self,
        user_id: str,
        scope: Optional[str] = None,
        agent_id: Optional[str] = None,
        category: Optional[str] = None,
        active_only: bool = True,
    ) -> List[MemoryModel]:
        stmt = select(MemoryModel).where(MemoryModel.user_id == user_id)
        if active_only:
            stmt = stmt.where(MemoryModel.is_active.is_(True))
        if scope:
            stmt = stmt.where(MemoryModel.scope == scope)
        if agent_id:
            stmt = stmt.where(MemoryModel.agent_id == agent_id)
        if category:
            stmt = stmt.where(MemoryModel.category == category)
        stmt = stmt.order_by(MemoryModel.created_at.desc())
        res = await self.session.execute(stmt)
        return list(res.scalars().all())

    async def list_household_memories(
        self,
        category: Optional[str] = None,
    ) -> List[MemoryModel]:
        stmt = select(MemoryModel).where(MemoryModel.scope == "household", MemoryModel.is_active.is_(True))
        if category:
            stmt = stmt.where(MemoryModel.category == category)
        stmt = stmt.order_by(MemoryModel.created_at.desc())
        res = await self.session.execute(stmt)
        return list(res.scalars().all())

    async def get_by_id(self, memory_id: str) -> Optional[MemoryModel]:
        res = await self.session.execute(select(MemoryModel).where(MemoryModel.id == memory_id))
        return res.scalars().first()

    async def create(self, memory: MemoryModel) -> MemoryModel:
        self.session.add(memory)
        await self.session.flush()
        return memory

    async def update(self, memory: MemoryModel) -> MemoryModel:
        self.session.add(memory)
        await self.session.flush()
        return memory

    async def delete(self, memory_id: str) -> None:
        model = await self.get_by_id(memory_id)
        if model:
            await self.session.delete(model)
            await self.session.flush()

    async def delete_personal_memories(self, user_id: str) -> None:
        await self.session.execute(
            delete(MemoryModel).where((MemoryModel.user_id == user_id) & (MemoryModel.scope == "personal"))
        )
        await self.session.flush()

    async def reassign_household_memories(self, from_user_id: str, to_user_id: str) -> None:
        await self.session.execute(
            update(MemoryModel)
            .where((MemoryModel.user_id == from_user_id) & (MemoryModel.scope == "household"))
            .values(user_id=to_user_id)
        )
        await self.session.flush()
