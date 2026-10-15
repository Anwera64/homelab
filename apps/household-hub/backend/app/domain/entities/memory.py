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
    # Memory spec §3.1. An expired memory is left out of the prompt and of recall.
    expires_at: datetime | None = None
    origin: str = "reflection"  # "reflection", "tool" or "manual"
    relevant_for: list[str] | None = None
    event_id: str | None = None
    # Set when the memory is retired, in favour of another or by the member, so it can be undone.
    replaced_by_id: str | None = None
    retired_at: datetime | None = None
    # For an event memory: the event's time before it last moved ("Moved in your calendar · was …").
    moved_from: datetime | None = None
    created_at: datetime = field(default_factory=get_utc_now)
    updated_at: datetime = field(default_factory=get_utc_now)


@dataclass
class MemoryPageItem:
    """One row of the memory page: the memory, who said it, which agent noted it, and whether ✕ shows."""

    memory: AgentMemory
    speaker_name: str
    is_mine: bool
    agent_name: str
    agent_avatar: str
    can_remove: bool


@dataclass
class MemoryPage:
    yours: list[MemoryPageItem]
    household: list[MemoryPageItem]
