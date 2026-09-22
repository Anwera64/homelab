from app.domain.entities.session import ConversationSession, ChatMessage
from app.data.datasources.session_data_source import SessionListRow
from app.data.models.session_model import SessionModel, MessageModel

# Long enough to recognise a conversation by, short enough that a list of forty rows is not a
# transcript. The phone never truncates: a row shows what it is given.
PREVIEW_LENGTH = 120


class SessionDataMapper:
    @staticmethod
    def to_domain_session_row(row: SessionListRow) -> ConversationSession:
        """A session with everything its Chats row draws — agent, and the last thing said."""
        session = SessionDataMapper.to_domain_session(row.session)
        agent = row.session.agent
        session.last_message_preview = SessionDataMapper._preview(row.last_message_preview)
        session.agent_name = agent.name if agent else None
        session.agent_avatar = agent.avatar if agent else None
        return session

    @staticmethod
    def _preview(content: str | None) -> str | None:
        if content is None:
            return None
        collapsed = " ".join(content.split())
        if len(collapsed) <= PREVIEW_LENGTH:
            return collapsed
        return collapsed[: PREVIEW_LENGTH - 1].rstrip() + "…"

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
