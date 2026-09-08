from typing import List, Optional, Tuple
from app.domain.entities.user import User
from app.domain.entities.session import ConversationSession, ChatMessage
from app.domain.repositories.session_repository import ISessionRepository
from app.domain.exceptions import EntityNotFoundException, ZeroLeakViolationException


class GetSessionUseCase:
    def __init__(self, session_repo: ISessionRepository):
        self.session_repo = session_repo

    async def execute(
        self,
        session_id: str,
        current_user: User,
        limit: int = 50,
        before_id: Optional[str] = None,
    ) -> Tuple[ConversationSession, List[ChatMessage]]:
        session = await self.session_repo.get_by_id(session_id)
        if not session:
            raise EntityNotFoundException("Session not found")

        if session.user_id != current_user.id:
            raise ZeroLeakViolationException("Zero-Leak Privacy violation: You cannot access another member's conversation session.")

        messages = await self.session_repo.get_messages(session_id=session.id, limit=limit, before_id=before_id)
        return session, messages
