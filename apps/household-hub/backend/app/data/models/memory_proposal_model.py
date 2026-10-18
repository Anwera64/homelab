import uuid
from datetime import datetime, timezone
from sqlalchemy import Column, String, Text, DateTime, ForeignKey

from app.data.models.base import Base


def get_utc_now():
    return datetime.now(timezone.utc)


class MemoryProposalModel(Base):
    """A pending change to a stored memory (memory spec §3.5, §6.1)."""

    __tablename__ = "memory_proposals"
    __table_args__ = {"extend_existing": True}

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    memory_id = Column(String(36), ForeignKey("agent_memories.id", ondelete="CASCADE"), nullable=False, index=True)
    content = Column(Text, nullable=False)
    # What the model named. Kept for the logs; never shown.
    relation = Column(String(32), nullable=False)
    # The member who said the stored memory. Only they can answer.
    answerer_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    source_session_id = Column(String(36), ForeignKey("conversation_sessions.id", ondelete="SET NULL"), nullable=True)
    source_user_id = Column(String(36), ForeignKey("users.id", ondelete="SET NULL"), nullable=True)
    state = Column(String(16), default="pending", nullable=False, index=True)  # pending, kept, both, replaced, dropped
    # The memory Keep both or Use new saved, so Undo can take it away again.
    new_memory_id = Column(String(36), ForeignKey("agent_memories.id", ondelete="SET NULL"), nullable=True)
    # "reflection", or "tool" when `remember` saved its sentence first and asked whoever said the stored one.
    origin = Column(String(16), default="reflection", server_default="reflection", nullable=False)
    created_at = Column(DateTime(timezone=True), default=get_utc_now, nullable=False)
    answered_at = Column(DateTime(timezone=True), nullable=True)
