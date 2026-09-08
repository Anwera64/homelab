from app.domain.entities.user import User
from app.domain.repositories.agent_repository import IAgentRepository
from app.domain.repositories.session_repository import ISessionRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.exceptions import EntityNotFoundException, ZeroLeakViolationException


class PurgeTrashAgentUseCase:
    def __init__(
        self,
        agent_repo: IAgentRepository,
        session_repo: ISessionRepository,
        uow: IUnitOfWork,
    ):
        self.agent_repo = agent_repo
        self.session_repo = session_repo
        self.uow = uow

    async def execute(self, agent_id: str, current_user: User) -> None:
        agent = await self.agent_repo.get_by_id(agent_id)
        if not agent or agent.deleted_at is None:
            raise EntityNotFoundException("Trashed model not found")

        if agent.owner_id != current_user.id and not current_user.is_admin:
            raise ZeroLeakViolationException("Only the model owner or an Admin can permanently purge this model.")

        async with self.uow:
            # Transition associated sessions to archived state
            await self.session_repo.archive_by_agent_id(agent.id)
            # Delete agent permanently
            await self.agent_repo.delete_permanent(agent.id)
            await self.uow.commit()
