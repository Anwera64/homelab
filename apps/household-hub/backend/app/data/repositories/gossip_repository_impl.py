from datetime import datetime
from typing import List, Optional
from app.domain.entities.gossip_milestone import GossipMilestone
from app.domain.repositories.gossip_repository import IGossipRepository
from app.data.datasources.gossip_data_source import IGossipDataSource
from app.data.mappers.gossip_data_mapper import GossipDataMapper


class GossipRepositoryImpl(IGossipRepository):
    def __init__(self, data_source: IGossipDataSource, mapper: GossipDataMapper):
        self.data_source = data_source
        self.mapper = mapper

    async def publish(self, milestone: GossipMilestone) -> GossipMilestone:
        model = self.mapper.to_model(milestone)
        created_model = await self.data_source.create(model)
        return self.mapper.to_entity(created_model)

    async def get_by_id(self, milestone_id: str) -> Optional[GossipMilestone]:
        model = await self.data_source.get_by_id(milestone_id)
        if not model:
            return None
        return self.mapper.to_entity(model)

    async def get_active_household_milestones(
        self, limit: int = 50, now: Optional[datetime] = None
    ) -> List[GossipMilestone]:
        models = await self.data_source.list_active_household(limit=limit, now=now)
        return [self.mapper.to_entity(m) for m in models]

    async def get_user_published_milestones(
        self, user_id: str, limit: int = 50
    ) -> List[GossipMilestone]:
        models = await self.data_source.list_by_source_user(user_id=user_id, limit=limit)
        return [self.mapper.to_entity(m) for m in models]

    async def revoke_milestone(self, milestone_id: str, user_id: str) -> bool:
        model = await self.data_source.get_by_id(milestone_id)
        if not model:
            return False
        model.is_active = False
        await self.data_source.update(model)
        return True

    async def reassign_household_milestones(self, from_user_id: str, to_user_id: str) -> int:
        return await self.data_source.reassign_household_milestones(from_user_id, to_user_id)

    async def delete_by_source_session_id(self, session_id: str) -> int:
        return await self.data_source.delete_by_source_session_id(session_id)
