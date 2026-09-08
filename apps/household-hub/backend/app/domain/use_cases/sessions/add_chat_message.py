from typing import Any, Dict, Optional
from app.domain.entities.user import User
from app.domain.entities.session import ChatMessage
from app.domain.repositories.session_repository import ISessionRepository
from app.domain.repositories.agent_repository import IAgentRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.exceptions import EntityNotFoundException, ZeroLeakViolationException, InvalidOperationException


class AddChatMessageUseCase:
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
        session_id: str,
        current_user: User,
        role: str,
        content: str,
        metadata_json: Optional[Dict[str, Any]] = None,
    ) -> ChatMessage:
        session = await self.session_repo.get_by_id(session_id)
        if not session:
            raise EntityNotFoundException("Session not found")

        if session.user_id != current_user.id:
            raise ZeroLeakViolationException("Zero-Leak Privacy violation: You cannot post in another member's session.")

        if session.is_archived or session.agent_id is None:
            raise InvalidOperationException("Cannot send messages to an archived conversation session.")

        agent = await self.agent_repo.get_by_id(session.agent_id)
        if agent and agent.deleted_at is not None:
            raise InvalidOperationException(
                "Cannot send messages while the agent is in trash. Restore the agent to continue chatting."
            )

        async with self.uow:
            message = ChatMessage(
                session_id=session.id,
                role=role,
                content=content,
                metadata_json=metadata_json or {},
            )
            created_msg = await self.session_repo.add_message(message)
            session.touch()
            await self.session_repo.update(session)
            await self.uow.commit()

        return created_msg
