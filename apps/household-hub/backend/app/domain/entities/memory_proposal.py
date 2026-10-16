from dataclasses import dataclass, field
from datetime import datetime, timezone
import uuid

# What reflection names a new fact against its closest stored memory (memory spec §6). Same is
# dropped, different is saved, and the other four raise a proposal.
SAME = "same"
DIFFERENT = "different"
MORE_DETAIL = "more_detail"
CHANGED = "changed"
CONTRADICTS = "contradicts"
UNCLEAR = "unclear"
PROPOSING = (MORE_DETAIL, CHANGED, CONTRADICTS, UNCLEAR)
RELATIONS = (SAME, DIFFERENT) + PROPOSING

PENDING = "pending"
DROPPED = "dropped"


def get_utc_now() -> datetime:
    return datetime.now(timezone.utc)


@dataclass
class MemoryProposal:
    """
    A pending change to a stored memory (memory spec §3.5, §6.1). The stored memory stays active until
    whoever said it answers. The relation is kept for the logs and never shown.
    """

    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    memory_id: str = ""
    content: str = ""
    relation: str = UNCLEAR
    answerer_id: str = ""
    source_session_id: str | None = None
    source_user_id: str | None = None
    state: str = PENDING  # pending, then kept, both, replaced or dropped
    created_at: datetime = field(default_factory=get_utc_now)
    answered_at: datetime | None = None
