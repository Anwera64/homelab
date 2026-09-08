from datetime import datetime, timezone
from typing import List, Optional, Protocol
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update, delete, or_

from app.data.models.gossip_model import GossipMilestoneModel


def get_utc_now() -> datetime:
    return datetime.now(timezone.utc)


class IGossipDataSource(Protocol):
    async def create(self, model: GossipMilestoneModel) -> GossipMilestoneModel:
        ...

    async def get_by_id(self, milestone_id: str) -> Optional[GossipMilestoneModel]:
        ...

    async def list_active_household(
        self, limit: int = 50, now: Optional[datetime] = None
    ) -> List[GossipMilestoneModel]:
        ...

    async def list_by_source_user(
        self, user_id: str, limit: int = 50
    ) -> List[GossipMilestoneModel]:
        ...

    async def update(self, model: GossipMilestoneModel) -> GossipMilestoneModel:
        ...

    async def reassign_household_milestones(
        self, from_user_id: str, to_user_id: str
    ) -> int:
        ...

    async def delete_by_source_session_id(self, session_id: str) -> int:
        ...


class SqliteGossipDataSource(IGossipDataSource):
    def __init__(self, session: AsyncSession):
        self.session = session

    async def create(self, model: GossipMilestoneModel) -> GossipMilestoneModel:
        self.session.add(model)
        await self.session.flush()
        return model

    async def get_by_id(self, milestone_id: str) -> Optional[GossipMilestoneModel]:
        res = await self.session.execute(
            select(GossipMilestoneModel).where(GossipMilestoneModel.id == milestone_id)
        )
        return res.scalars().first()

    async def list_active_household(
        self, limit: int = 50, now: Optional[datetime] = None
    ) -> List[GossipMilestoneModel]:
        current_time = now or get_utc_now()
        stmt = (
            select(GossipMilestoneModel)
            .where(
                GossipMilestoneModel.target_scope == "household",
                GossipMilestoneModel.is_active.is_(True),
                or_(
                    GossipMilestoneModel.expires_at.is_(None),
                    GossipMilestoneModel.expires_at > current_time,
                ),
            )
            .order_by(GossipMilestoneModel.created_at.desc())
            .limit(limit)
        )
        res = await self.session.execute(stmt)
        return list(res.scalars().all())

    async def list_by_source_user(
        self, user_id: str, limit: int = 50
    ) -> List[GossipMilestoneModel]:
        stmt = (
            select(GossipMilestoneModel)
            .where(GossipMilestoneModel.source_user_id == user_id)
            .order_by(GossipMilestoneModel.created_at.desc())
            .limit(limit)
        )
        res = await self.session.execute(stmt)
        return list(res.scalars().all())

    async def update(self, model: GossipMilestoneModel) -> GossipMilestoneModel:
        self.session.add(model)
        await self.session.flush()
        return model

    async def reassign_household_milestones(
        self, from_user_id: str, to_user_id: str
    ) -> int:
        res = await self.session.execute(
            update(GossipMilestoneModel)
            .where(
                (GossipMilestoneModel.source_user_id == from_user_id)
                & (GossipMilestoneModel.target_scope == "household")
            )
            .values(source_user_id=to_user_id)
        )
        await self.session.flush()
        return res.rowcount

    async def delete_by_source_session_id(self, session_id: str) -> int:
        res = await self.session.execute(
            delete(GossipMilestoneModel).where(
                GossipMilestoneModel.source_session_id == session_id
            )
        )
        await self.session.flush()
        return res.rowcount
