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

    def touch(self) -> None:
        self.updated_at = get_utc_now()
