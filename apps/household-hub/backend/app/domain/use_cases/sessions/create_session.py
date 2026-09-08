from typing import Optional
from app.domain.entities.user import User
from app.domain.entities.session import ConversationSession
from app.domain.repositories.session_repository import ISessionRepository
from app.domain.repositories.agent_repository import IAgentRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.exceptions import EntityNotFoundException


class CreateSessionUseCase:
    def __init__(
        self,
        session_repo: ISessionRepository,
        agent_repo: IAgentRepository,
        uow: IUnitOfWork,
    ):
        self.session_repo = session_repo
        self.agent_repo = agent_repo
        self.uow = uow

    async def execute(
        self,
        current_user: User,
        agent_id: str,
        title: Optional[str] = None,
        is_secret: bool = False,
    ) -> ConversationSession:
        agent = await self.agent_repo.get_by_id(agent_id)
        if not agent or agent.deleted_at is not None:
            raise EntityNotFoundException("Agent personality not found")

        session_title = title or f"Chat with {agent.name}"
        async with self.uow:
            session = ConversationSession(
                user_id=current_user.id,
                agent_id=agent.id,
                title=session_title,
                is_secret=bool(is_secret),
                is_archived=False,
            )
            created = await self.session_repo.create(session)
            await self.uow.commit()

        return created
