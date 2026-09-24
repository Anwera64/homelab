from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any, Dict
import uuid


def get_utc_now() -> datetime:
    return datetime.now(timezone.utc)


@dataclass
class ChatMessage:
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    session_id: str = ""
    role: str = "user"  # "user", "assistant", "system"
    content: str = ""
    metadata_json: Dict[str, Any] = field(default_factory=dict)
    created_at: datetime = field(default_factory=get_utc_now)


@dataclass
class ConversationSession:
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    user_id: str = ""
    agent_id: str | None = None
    title: str = "New Conversation"
    is_secret: bool = False
    is_archived: bool = False
    created_at: datetime = field(default_factory=get_utc_now)
    updated_at: datetime = field(default_factory=get_utc_now)

    # The conversation so far, compressed: everything up to and including [summarized_through_id],
    # in the model's own summary. Only messages after it are sent word for word. Written on its own
    # by save_history_summary, never by update, so a turn saving the session can't undo it.
    history_summary: str | None = None
    summarized_through_id: str | None = None

    # What a Chats row draws beside the title. They belong to the agent and the newest message
    # rather than to the session itself, which is why they are read-only here: nothing sets them
    # on the way in, and the list query fills them on the way out.
    last_message_preview: str | None = None
    agent_name: str | None = None
    agent_avatar: str | None = None

    def touch(self) -> None:
        self.updated_at = get_utc_now()
