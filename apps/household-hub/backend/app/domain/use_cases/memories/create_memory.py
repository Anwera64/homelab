from typing import Optional
from app.domain.entities.memory import AgentMemory
from app.domain.repositories.memory_repository import IMemoryRepository
from app.domain.repositories.session_repository import ISessionRepository
from app.domain.repositories.agent_repository import IAgentRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.exceptions import (
    EntityNotFoundException,
    ZeroLeakViolationException,
    SecretModeViolationException,
    InvalidOperationException,
)


class CreateMemoryUseCase:
    def __init__(
        self,
        memory_repo: IMemoryRepository,
        session_repo: ISessionRepository,
        agent_repo: IAgentRepository,
        uow: IUnitOfWork,
    ):
        self.memory_repo = memory_repo
        self.session_repo = session_repo
        self.agent_repo = agent_repo
        self.uow = uow

    async def execute(
        self,
        user_id: str,
        content: str,
        scope: str = "personal",
        category: str = "fact",
        confidence: float = 1.0,
        agent_id: Optional[str] = None,
        source_session_id: Optional[str] = None,
    ) -> AgentMemory:
        if scope not in ["personal", "household"]:
            raise InvalidOperationException("Scope must be either 'personal' or 'household'.")

        if source_session_id:
            session = await self.session_repo.get_by_id(source_session_id)
            if not session:
                raise EntityNotFoundException("Source session not found")

            if session.user_id != user_id:
                raise ZeroLeakViolationException("Zero-Leak Privacy violation: Cannot link memory to another member's session.")

            if session.is_secret and scope == "household":
                raise SecretModeViolationException(
                    "Cannot publish household memory from a secret session. Zero-Leak confidentiality enforced."
                )

        if agent_id:
            agent = await self.agent_repo.get_by_id(agent_id)
            if not agent or agent.deleted_at is not None:
                raise EntityNotFoundException("Referenced agent personality not found")

        async with self.uow:
            memory = AgentMemory(
                user_id=user_id,
                agent_id=agent_id,
                scope=scope,
                category=category,
                content=content,
                confidence=confidence,
                source_session_id=source_session_id,
                is_active=True,
            )
            created = await self.memory_repo.create(memory)
            await self.uow.commit()

        return created
