import uuid
from datetime import datetime, timezone
from sqlalchemy import Column, String, Text, Float, Boolean, DateTime, ForeignKey
from sqlalchemy.orm import relationship

from app.core.database import Base


def get_utc_now():
    return datetime.now(timezone.utc)


class AgentMemory(Base):
    __tablename__ = "agent_memories"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    agent_id = Column(String(36), ForeignKey("agent_personalities.id", ondelete="SET NULL"), nullable=True, index=True)
    scope = Column(String(32), default="personal", nullable=False, index=True)  # 'personal' or 'household'
    category = Column(String(64), default="fact", nullable=False, index=True)  # 'preference', 'fact', 'milestone', 'health', 'project'
    content = Column(Text, nullable=False)
    confidence = Column(Float, default=1.0, nullable=False)
    source_session_id = Column(String(36), ForeignKey("conversation_sessions.id", ondelete="SET NULL"), nullable=True)
    is_active = Column(Boolean, default=True, nullable=False)
    created_at = Column(DateTime(timezone=True), default=get_utc_now, nullable=False)
    updated_at = Column(DateTime(timezone=True), default=get_utc_now, onupdate=get_utc_now, nullable=False)

    # Relationships
    user = relationship("User", back_populates="memories")
    agent = relationship("AgentPersonality")
    source_session = relationship("ConversationSession")
