from datetime import datetime
from typing import List, Optional

from app.domain.entities.user import User
from app.domain.entities.gossip_milestone import GossipMilestone
from app.domain.repositories.gossip_repository import IGossipRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.exceptions import EntityNotFoundException, ZeroLeakViolationException


class ListHouseholdMilestonesUseCase:
    def __init__(self, gossip_repo: IGossipRepository):
        self.gossip_repo = gossip_repo

    async def execute(self, limit: int = 50, now: Optional[datetime] = None) -> List[GossipMilestone]:
        return await self.gossip_repo.get_active_household_milestones(limit=limit, now=now)


class ListUserMilestonesAuditUseCase:
    def __init__(self, gossip_repo: IGossipRepository):
        self.gossip_repo = gossip_repo

    async def execute(self, user_id: str, limit: int = 50) -> List[GossipMilestone]:
        return await self.gossip_repo.get_user_published_milestones(user_id=user_id, limit=limit)


class RevokeGossipMilestoneUseCase:
    def __init__(self, gossip_repo: IGossipRepository, uow: IUnitOfWork):
        self.gossip_repo = gossip_repo
        self.uow = uow

    async def execute(self, milestone_id: str, current_user: User) -> bool:
        milestone = await self.gossip_repo.get_by_id(milestone_id)
        if not milestone:
            raise EntityNotFoundException("Gossip milestone not found.")

        # Only author or admin can revoke
        if milestone.source_user_id != current_user.id and not current_user.is_admin:
            raise ZeroLeakViolationException(
                "Zero-Leak Privacy violation: You can only revoke milestones authored by yourself."
            )

        async with self.uow:
            revoked = await self.gossip_repo.revoke_milestone(milestone_id, current_user.id)
            await self.uow.commit()
            return revoked
