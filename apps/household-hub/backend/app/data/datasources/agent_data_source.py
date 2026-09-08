from typing import Protocol, List, Optional
from datetime import datetime
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update, delete

from app.data.models.agent_model import AgentModel


class IAgentDataSource(Protocol):
    async def list_active(self) -> List[AgentModel]:
        ...

    async def list_trash(self, owner_id: Optional[str] = None, is_admin: bool = False) -> List[AgentModel]:
        ...

    async def get_by_id(self, agent_id: str) -> Optional[AgentModel]:
        ...

    async def get_by_slug(self, slug: str, include_deleted: bool = False) -> Optional[AgentModel]:
        ...

    async def get_by_id_or_slug(self, id_or_slug: str) -> Optional[AgentModel]:
        ...

    async def create(self, agent: AgentModel) -> AgentModel:
        ...

    async def update(self, agent: AgentModel) -> AgentModel:
        ...

    async def delete_permanent(self, agent_id: str) -> None:
        ...

    async def get_expired_trash_ids(self, cutoff: datetime) -> List[str]:
        ...

    async def purge_expired_trash(self, cutoff: datetime) -> List[str]:
        ...

    async def reassign_owner(self, from_user_id: str, to_user_id: str) -> None:
        ...


class SqliteAgentDataSource(IAgentDataSource):
    def __init__(self, session: AsyncSession):
        self.session = session

    async def list_active(self) -> List[AgentModel]:
        stmt = (
            select(AgentModel)
            .where(AgentModel.deleted_at.is_(None))
            .where(AgentModel.is_active.is_(True))
            .order_by(AgentModel.is_builtin.desc(), AgentModel.created_at)
        )
        res = await self.session.execute(stmt)
        return list(res.scalars().all())

    async def list_trash(self, owner_id: Optional[str] = None, is_admin: bool = False) -> List[AgentModel]:
        stmt = select(AgentModel).where(AgentModel.deleted_at.is_not(None))
        if not is_admin and owner_id:
            stmt = stmt.where(AgentModel.owner_id == owner_id)
        res = await self.session.execute(stmt)
        return list(res.scalars().all())

    async def get_by_id(self, agent_id: str) -> Optional[AgentModel]:
        res = await self.session.execute(select(AgentModel).where(AgentModel.id == agent_id))
        return res.scalars().first()

    async def get_by_slug(self, slug: str, include_deleted: bool = False) -> Optional[AgentModel]:
        stmt = select(AgentModel).where(AgentModel.slug == slug)
        if not include_deleted:
            stmt = stmt.where(AgentModel.deleted_at.is_(None))
        res = await self.session.execute(stmt)
        return res.scalars().first()

    async def get_by_id_or_slug(self, id_or_slug: str) -> Optional[AgentModel]:
        stmt = select(AgentModel).where(
            ((AgentModel.id == id_or_slug) | (AgentModel.slug == id_or_slug))
            & AgentModel.deleted_at.is_(None)
        )
        res = await self.session.execute(stmt)
        return res.scalars().first()

    async def create(self, agent: AgentModel) -> AgentModel:
        self.session.add(agent)
        await self.session.flush()
        return agent

    async def update(self, agent: AgentModel) -> AgentModel:
        self.session.add(agent)
        await self.session.flush()
        return agent

    async def delete_permanent(self, agent_id: str) -> None:
        agent = await self.get_by_id(agent_id)
        if agent:
            await self.session.delete(agent)
            await self.session.flush()

    async def get_expired_trash_ids(self, cutoff: datetime) -> List[str]:
        stmt = select(AgentModel.id).where(
            AgentModel.deleted_at.is_not(None),
            AgentModel.deleted_at < cutoff,
        )
        res = await self.session.execute(stmt)
        return list(res.scalars().all())

    async def purge_expired_trash(self, cutoff: datetime) -> List[str]:
        stmt = select(AgentModel).where(
            AgentModel.deleted_at.is_not(None),
            AgentModel.deleted_at < cutoff,
        )
        res = await self.session.execute(stmt)
        expired = res.scalars().all()
        purged_ids = []
        for exp in expired:
            purged_ids.append(exp.id)
            await self.session.delete(exp)
        await self.session.flush()
        return purged_ids

    async def reassign_owner(self, from_user_id: str, to_user_id: str) -> None:
        await self.session.execute(
            update(AgentModel)
            .where(AgentModel.owner_id == from_user_id)
            .values(owner_id=to_user_id)
        )
        await self.session.flush()
