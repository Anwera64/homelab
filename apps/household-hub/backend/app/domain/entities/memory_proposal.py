from dataclasses import dataclass, field
from datetime import datetime, timezone
import uuid

from app.domain.entities.memory import MemoryPageItem

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
# A `remember` the member who asked took back before it was answered (✕ on its waiting card, or forget).
WITHDRAWN = "withdrawn"
# The three answers (memory spec §6.1): Keep stored, Keep both, Use new.
KEPT = "kept"
BOTH = "both"
REPLACED = "replaced"
ANSWERS = (KEPT, BOTH, REPLACED)

# Where a proposal came from: a sentence reflection read, or one a member asked to `remember` over
# another member's memory. Either way nothing is saved until whoever said the stored one answers.
FROM_REFLECTION = "reflection"
FROM_REMEMBER = "tool"


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
    state: str = PENDING  # pending, then kept, both, replaced, dropped or withdrawn
    # The memory Keep both or Use new saved, so Undo can take it away again.
    new_memory_id: str | None = None
    origin: str = FROM_REFLECTION
    # A `remember`'s end date, given to the memory once it's saved.
    expires_at: datetime | None = None
    created_at: datetime = field(default_factory=get_utc_now)
    answered_at: datetime | None = None


@dataclass
class ChangeCard:
    """One change as its card draws it (memory spec §6.1): the stored memory, the new sentence and its source.

    The chat's title is only for the chat's owner; anyone else sees whose chat it was.
    """

    proposal: MemoryProposal
    stored: MemoryPageItem
    can_answer: bool
    answerer_name: str
    source_title: str | None
    source_owner_name: str


@dataclass
class ChangesToCheck:
    """The member's changes to answer, then the ones from their chats waiting for someone else."""

    to_check: list[ChangeCard]
    waiting: list[ChangeCard]


@dataclass
class LookedOverSummary:
    """The quiet line in a chat after the last message looked over (memory spec §8)."""

    through_message_id: str | None
    at: datetime
    remembered: int
    to_check: int


@dataclass
class LookedOver:
    """The chat's sheet, "Remembered from this chat": changes to check, waiting ones, and what was saved."""

    at: datetime
    to_check: list[ChangeCard]
    waiting: list[ChangeCard]
    remembered: list[MemoryPageItem]
