from datetime import datetime, timezone
from typing import Optional
from app.domain.entities.user import User
from app.domain.repositories.agent_repository import IAgentRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.use_cases.chat.tool_approval import DropPendingProposalsUseCase
from app.domain.exceptions import EntityNotFoundException, ZeroLeakViolationException, InvalidOperationException


class SoftDeleteAgentUseCase:
    def __init__(
        self,
        agent_repo: IAgentRepository,
        uow: IUnitOfWork,
        drop_proposals: Optional[DropPendingProposalsUseCase] = None,
    ):
        self.agent_repo = agent_repo
        self.uow = uow
        # An agent in the trash takes the cards its chats were waiting on with it.
        self.drop_proposals = drop_proposals

    async def execute(self, agent_id: str, current_user: User) -> None:
        agent = await self.agent_repo.get_by_id(agent_id)
        if not agent or agent.deleted_at is not None:
            raise EntityNotFoundException("Agent personality not found")

        if agent.is_builtin:
            raise InvalidOperationException("Built-in system models cannot be deleted.")

        if agent.owner_id != current_user.id:
            raise ZeroLeakViolationException("Only the model owner can delete this model.")

        async with self.uow:
            agent.deleted_at = datetime.now(timezone.utc)
            updated = await self.agent_repo.update(agent)
            await self.uow.commit()

        if self.drop_proposals is not None:
            await self.drop_proposals.for_agent(agent.id)

        return updated
