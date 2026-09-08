from datetime import datetime, timezone
from app.domain.entities.user import User
from app.domain.entities.agent import AgentPersonality
from app.domain.repositories.agent_repository import IAgentRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.exceptions import (
    EntityNotFoundException,
    ZeroLeakViolationException,
    TrashGracePeriodException,
    InvalidOperationException,
)


class RestoreAgentUseCase:
    def __init__(self, agent_repo: IAgentRepository, uow: IUnitOfWork, grace_days: int = 7):
        self.agent_repo = agent_repo
        self.uow = uow
        self.grace_days = grace_days

    async def execute(self, agent_id: str, current_user: User) -> AgentPersonality:
        agent = await self.agent_repo.get_by_id(agent_id)
        if not agent or agent.deleted_at is None:
            raise EntityNotFoundException("Trashed model not found")

        if agent.owner_id != current_user.id and not current_user.is_admin:
            raise ZeroLeakViolationException("Only the model owner or an Admin can restore this model.")

        now = datetime.now(timezone.utc)
        if not agent.can_restore(now=now, grace_days=self.grace_days):
            raise TrashGracePeriodException(
                f"The {self.grace_days}-day grace period for restoring this model has expired. Model has been purged."
            )

        # Check slug conflict with any active model
        active_conflict = await self.agent_repo.get_by_slug(agent.slug, include_deleted=False)
        if active_conflict and active_conflict.id != agent.id:
            raise InvalidOperationException(
                f"Cannot restore: An active model with slug '{agent.slug}' has already been created. Purge or rename before restoring."
            )

        async with self.uow:
            agent.deleted_at = None
            updated = await self.agent_repo.update(agent)
            await self.uow.commit()

        return updated
