from typing import Protocol, List, Optional
from app.domain.entities.session import ConversationSession, ChatMessage


class ISessionRepository(Protocol):
    async def list_by_user_id(self, user_id: str) -> List[ConversationSession]:
        ...

    async def get_by_id(self, session_id: str) -> Optional[ConversationSession]:
        ...

    async def create(self, session: ConversationSession) -> ConversationSession:
        ...

    async def update(self, session: ConversationSession) -> ConversationSession:
        ...

    async def delete(self, session_id: str) -> None:
        ...

    async def archive_by_agent_id(self, agent_id: str) -> None:
        ...

    async def add_message(self, message: ChatMessage) -> ChatMessage:
        ...

    async def get_messages(self, session_id: str, limit: int = 50, before_id: Optional[str] = None) -> List[ChatMessage]:
        ...

    async def get_messages_after(
        self, session_id: str, after_id: Optional[str], limit: int = 200
    ) -> List[ChatMessage]:
        """The messages after [after_id] (all of them when None), oldest first, at most the newest [limit]."""
        ...

    async def save_history_summary(self, session_id: str, summary: str, through_id: str) -> None:
        """Stores the summary and the last message it covers, and nothing else about the session."""
        ...
