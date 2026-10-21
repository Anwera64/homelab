from dataclasses import dataclass, field
from datetime import datetime, timezone
import uuid

# How a memory rebuilt from its event shows the event's time (memory spec §7). Its content is then
# only the title: every reader adds the time in its own zone, since the hub keeps times in UTC.
TIMED = "timed"
ALL_DAY = "all_day"


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
    # The key of the member's calendar the event is on. None for a link made before calendars could
    # be picked: the first check finds its calendar and fills it in.
    event_calendar: str | None = None
    # Set when the memory is retired, in favour of another or by the member, so it can be undone.
    replaced_by_id: str | None = None
    retired_at: datetime | None = None
    # For an event memory: the event's time before it last moved ("Moved in your calendar · was …").
    moved_from: datetime | None = None
    # For an event memory: the event's start when the memory was last made to match it, so the
    # periodic check can tell it moved. An all-day event starts at midnight UTC on its date.
    event_starts_at: datetime | None = None
    # TIMED or ALL_DAY once the hub rebuilt the sentence from the event; None for the model's own words.
    time_from_event: str | None = None
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
class WaitingChange:
    """A sentence the member asked to remember over someone else's memory, still waiting on their answer:
    its card on the memory page shows it beside the stored memory, which agents keep using until then."""

    proposal_id: str
    content: str
    agent_name: str
    created_at: datetime
    answerer_name: str
    stored: MemoryPageItem
    expires_at: datetime | None = None


@dataclass
class MemoryPage:
    yours: list[MemoryPageItem]
    household: list[MemoryPageItem]
    # The member's changes to check: the band on the page and the chip on Profile.
    to_check: int = 0
    waiting: list[WaitingChange] = field(default_factory=list)
