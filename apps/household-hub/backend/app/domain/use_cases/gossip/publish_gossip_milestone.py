from datetime import datetime
from typing import Any, Dict, Optional

from app.domain.entities.gossip_milestone import GossipMilestone
from app.domain.repositories.gossip_repository import IGossipRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.exceptions import SecretModeViolationException


class PublishGossipMilestoneUseCase:
    def __init__(self, gossip_repo: IGossipRepository, uow: IUnitOfWork):
        self.gossip_repo = gossip_repo
        self.uow = uow

    async def execute(
        self,
        source_user_id: str,
        source_username: str,
        reporting_agent_id: Optional[str],
        reporting_agent_name: str,
        summary: str,
        category: str = "milestone",
        target_scope: str = "household",
        details_json: Optional[Dict[str, Any]] = None,
        expires_at: Optional[datetime] = None,
        source_session_id: Optional[str] = None,
        is_secret_session: bool = False,
    ) -> GossipMilestone:
        if is_secret_session:
            raise SecretModeViolationException(
                "Cannot publish milestones to the Gossip Bus from a Secret Mode conversation."
            )

        milestone = GossipMilestone(
            source_user_id=source_user_id,
            source_username=source_username,
            reporting_agent_id=reporting_agent_id,
            reporting_agent_name=reporting_agent_name,
            target_scope=target_scope,
            category=category,
            summary=summary,
            details_json=details_json or {},
            expires_at=expires_at,
            source_session_id=source_session_id,
            is_active=True,
        )

        sanitized_summary = milestone.sanitize()
        milestone.summary = sanitized_summary

        async with self.uow:
            # Semantic deduplication check: if an active milestone with same summary exists for target_scope, reuse it
            active_existing = await self.gossip_repo.get_active_household_milestones(limit=100)
            for existing in active_existing:
                if (
                    existing.target_scope == target_scope
                    and existing.summary.strip().lower() == sanitized_summary.strip().lower()
                ):
                    return existing

            created = await self.gossip_repo.publish(milestone)
            await self.uow.commit()
            return created
