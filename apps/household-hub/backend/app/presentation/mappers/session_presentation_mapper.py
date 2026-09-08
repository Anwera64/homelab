from typing import List
from app.domain.entities.session import ConversationSession, ChatMessage
from app.presentation.schemas.session_schemas import SessionRead, SessionDetailRead, ChatMessageRead


class SessionPresentationMapper:
    @staticmethod
    def to_response(entity: ConversationSession) -> SessionRead:
        return SessionRead(
            id=entity.id,
            user_id=entity.user_id,
            agent_id=entity.agent_id,
            title=entity.title,
            is_secret=entity.is_secret,
            is_archived=entity.is_archived,
            created_at=entity.created_at,
            updated_at=entity.updated_at,
        )

    @staticmethod
    def to_message_response(entity: ChatMessage) -> ChatMessageRead:
        return ChatMessageRead(
            id=entity.id,
            session_id=entity.session_id,
            role=entity.role,
            content=entity.content,
            metadata_json=entity.metadata_json or {},
            created_at=entity.created_at,
        )

    @staticmethod
    def to_detail_response(session: ConversationSession, messages: List[ChatMessage]) -> SessionDetailRead:
        session_read = SessionPresentationMapper.to_response(session)
        msg_reads = [SessionPresentationMapper.to_message_response(m) for m in messages]
        return SessionDetailRead(
            **session_read.model_dump(),
            messages=msg_reads,
        )
