import uuid
from datetime import datetime, timezone
from sqlalchemy import Column, String, Text, Float, Boolean, DateTime, ForeignKey, JSON
from sqlalchemy.orm import relationship

from app.data.models.base import Base


def get_utc_now():
    return datetime.now(timezone.utc)


class MemoryModel(Base):
    __tablename__ = "agent_memories"
    __table_args__ = {"extend_existing": True}

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    agent_id = Column(String(36), ForeignKey("agent_personalities.id", ondelete="SET NULL"), nullable=True, index=True)
    scope = Column(String(32), default="personal", nullable=False, index=True)  # 'personal' or 'household'
    category = Column(String(64), default="fact", nullable=False, index=True)  # 'preference', 'fact', 'milestone', 'health', 'project'
    content = Column(Text, nullable=False)
    confidence = Column(Float, default=1.0, nullable=False)
    source_session_id = Column(String(36), ForeignKey("conversation_sessions.id", ondelete="SET NULL"), nullable=True)
    is_active = Column(Boolean, default=True, nullable=False)
    # Memory spec §3.1.
    expires_at = Column(DateTime(timezone=True), nullable=True)
    origin = Column(String(16), default="reflection", server_default="reflection", nullable=False)  # 'reflection', 'tool', 'manual'
    relevant_for = Column(JSON, nullable=True)
    event_id = Column(String(255), nullable=True, index=True)
    # The key of the member's calendar the event is on (calendar_sources.key); None for older links.
    event_calendar = Column(String(16), nullable=True)
    replaced_by_id = Column(String(36), ForeignKey("agent_memories.id", ondelete="SET NULL"), nullable=True)
    retired_at = Column(DateTime(timezone=True), nullable=True)
    moved_from = Column(DateTime(timezone=True), nullable=True)
    # Memory spec §7: the event's start the memory was last matched to, and how its time is shown.
    event_starts_at = Column(DateTime(timezone=True), nullable=True)
    time_from_event = Column(String(8), nullable=True)  # 'timed', 'all_day', or None for the model's words
    created_at = Column(DateTime(timezone=True), default=get_utc_now, nullable=False)
    updated_at = Column(DateTime(timezone=True), default=get_utc_now, onupdate=get_utc_now, nullable=False)

    # Relationships
    user = relationship("UserModel", back_populates="memories")
    agent = relationship("AgentModel")
    source_session = relationship("SessionModel")
