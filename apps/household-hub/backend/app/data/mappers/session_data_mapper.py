from app.domain.entities.session import ConversationSession, ChatMessage
from app.data.models.session_model import SessionModel, MessageModel


class SessionDataMapper:
    @staticmethod
    def to_domain_session(model: SessionModel) -> ConversationSession:
        return ConversationSession(
            id=model.id,
            user_id=model.user_id,
            agent_id=model.agent_id,
            title=model.title,
            is_secret=model.is_secret,
            is_archived=model.is_archived,
            created_at=model.created_at,
            updated_at=model.updated_at,
        )

    @staticmethod
    def to_model_session(entity: ConversationSession) -> SessionModel:
        return SessionModel(
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
    def to_domain_message(model: MessageModel) -> ChatMessage:
        return ChatMessage(
            id=model.id,
            session_id=model.session_id,
            role=model.role,
            content=model.content,
            metadata_json=model.metadata_json or {},
            created_at=model.created_at,
        )

    @staticmethod
    def to_model_message(entity: ChatMessage) -> MessageModel:
        return MessageModel(
            id=entity.id,
            session_id=entity.session_id,
            role=entity.role,
            content=entity.content,
            metadata_json=entity.metadata_json or {},
            created_at=entity.created_at,
        )
