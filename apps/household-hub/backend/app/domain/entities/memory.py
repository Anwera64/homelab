from dataclasses import dataclass, field
from datetime import datetime, timezone
import uuid


def get_utc_now() -> datetime:
    return datetime.now(timezone.utc)


@dataclass
class AgentMemory:
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    user_id: str = ""
    agent_id: str | None = None
    scope: str = "personal"  # "personal" or "household"
    category: str = "fact"  # "preference", "fact", "milestone", "health", "project"
    content: str = ""
    confidence: float = 1.0
    source_session_id: str | None = None
    is_active: bool = True
    created_at: datetime = field(default_factory=get_utc_now)
    updated_at: datetime = field(default_factory=get_utc_now)
