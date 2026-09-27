from typing import List
from app.domain.entities.session import ConversationSession, ChatMessage
from app.domain.use_cases.chat.tool_approval import PAUSED_TURN, is_awaiting_approval
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
            last_message_preview=entity.last_message_preview,
            agent_name=entity.agent_name,
            agent_avatar=entity.agent_avatar,
        )

    @staticmethod
    def to_message_response(entity: ChatMessage) -> ChatMessageRead:
        return ChatMessageRead(
            id=entity.id,
            session_id=entity.session_id,
            role=entity.role,
            content=entity.content,
            # What a paused turn keeps to carry on is the model's, never the phone's.
            metadata_json={k: v for k, v in (entity.metadata_json or {}).items() if k != PAUSED_TURN},
            created_at=entity.created_at,
        )

    @staticmethod
    def to_detail_response(session: ConversationSession, messages: List[ChatMessage]) -> SessionDetailRead:
        session_read = SessionPresentationMapper.to_response(session)
        msg_reads = [SessionPresentationMapper.to_message_response(m) for m in messages]
        return SessionDetailRead(
            **session_read.model_dump(),
            messages=msg_reads,
            awaiting_approval=is_awaiting_approval(messages),
        )
