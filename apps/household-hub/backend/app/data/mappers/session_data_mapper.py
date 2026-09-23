import re

from app.domain.entities.session import ConversationSession, ChatMessage
from app.data.datasources.session_data_source import SessionListRow
from app.data.models.session_model import SessionModel, MessageModel

# Long enough to recognise a conversation by, short enough that a list of forty rows is not a
# transcript. The phone never truncates: a row shows what it is given.
PREVIEW_LENGTH = 120

# A Chats row is plain text, not a rendered document, so Markdown syntax is noise there. Each
# pattern below unwraps one construct down to the text a reader cares about, and the order they
# run in (below) matters: list markers are stripped before heading hashes, so a numbered list
# ("1. One") loses its number while a heading that merely starts with a number ("### 1. Play")
# does not — its line only starts with a digit once "### " is already gone.
_FENCED_CODE_RE = re.compile(r"```[^\n]*\n(.*?)```", re.DOTALL)
_INLINE_CODE_RE = re.compile(r"`([^`]+)`")
_IMAGE_RE = re.compile(r"!\[([^\]]*)\]\([^)]*\)")
_LINK_RE = re.compile(r"\[([^\]]*)\]\([^)]*\)")
_TABLE_SEPARATOR_RE = re.compile(r"^(?=.*-)(?=.*\|)[ \t|:-]+$", re.MULTILINE)
_DIVIDER_RE = re.compile(r"^[ \t]*([-*_])(?:[ \t]*\1){2,}[ \t]*$", re.MULTILINE)
_NUMBERED_LIST_RE = re.compile(r"^[ \t]*\d+\.[ \t]+", re.MULTILINE)
_BULLET_RE = re.compile(r"^[ \t]*[-*+][ \t]+", re.MULTILINE)
_HEADING_RE = re.compile(r"^[ \t]*#{1,6}[ \t]+", re.MULTILINE)
_BLOCKQUOTE_RE = re.compile(r"^[ \t]*>[ \t]?", re.MULTILINE)
_BOLD_STAR_RE = re.compile(r"(?<!\w)\*\*(\S(?:.*?\S)?)\*\*(?!\w)")
_BOLD_UNDERSCORE_RE = re.compile(r"(?<!\w)__(\S(?:.*?\S)?)__(?!\w)")
_ITALIC_STAR_RE = re.compile(r"(?<!\w)\*(\S(?:.*?\S)?)\*(?!\w)")
_ITALIC_UNDERSCORE_RE = re.compile(r"(?<!\w)_(\S(?:.*?\S)?)_(?!\w)")


def _strip_markdown_markers(content: str) -> str:
    """Unwrap Markdown syntax to the text underneath it, leaving whitespace for the caller to collapse."""
    content = _FENCED_CODE_RE.sub(r"\1", content)
    content = _INLINE_CODE_RE.sub(r"\1", content)
    content = _IMAGE_RE.sub(r"\1", content)
    content = _LINK_RE.sub(r"\1", content)
    content = _TABLE_SEPARATOR_RE.sub("", content)
    content = _DIVIDER_RE.sub("", content)
    content = _NUMBERED_LIST_RE.sub("", content)
    content = _BULLET_RE.sub("", content)
    content = _HEADING_RE.sub("", content)
    content = _BLOCKQUOTE_RE.sub("", content)
    content = _BOLD_STAR_RE.sub(r"\1", content)
    content = _BOLD_UNDERSCORE_RE.sub(r"\1", content)
    content = _ITALIC_STAR_RE.sub(r"\1", content)
    content = _ITALIC_UNDERSCORE_RE.sub(r"\1", content)
    return content.replace("|", " ")


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
        collapsed = " ".join(_strip_markdown_markers(content).split())
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
