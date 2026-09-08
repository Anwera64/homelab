from datetime import datetime
from typing import List, Optional, Protocol, runtime_checkable
from app.domain.entities.gossip_milestone import GossipMilestone


@runtime_checkable
class IGossipRepository(Protocol):
    async def publish(self, milestone: GossipMilestone) -> GossipMilestone:
        ...

    async def get_by_id(self, milestone_id: str) -> Optional[GossipMilestone]:
        ...

    async def get_active_household_milestones(
        self, limit: int = 50, now: Optional[datetime] = None
    ) -> List[GossipMilestone]:
        ...

    async def get_user_published_milestones(
        self, user_id: str, limit: int = 50
    ) -> List[GossipMilestone]:
        ...

    async def revoke_milestone(self, milestone_id: str, user_id: str) -> bool:
        ...

    async def reassign_household_milestones(self, from_user_id: str, to_user_id: str) -> int:
        ...

    async def delete_by_source_session_id(self, session_id: str) -> int:
        ...
