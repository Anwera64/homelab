from datetime import datetime, timezone
import uuid
from sqlalchemy import Column, String, Text, Boolean, DateTime, ForeignKey, JSON
from sqlalchemy.orm import relationship

from app.data.models.base import Base


def get_utc_now():
    return datetime.now(timezone.utc)


class GossipMilestoneModel(Base):
    __tablename__ = "gossip_milestones"
    __table_args__ = {"extend_existing": True}

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    source_user_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    source_username = Column(String(64), nullable=False, default="")
    reporting_agent_id = Column(String(36), ForeignKey("agent_personalities.id", ondelete="SET NULL"), nullable=True, index=True)
    reporting_agent_name = Column(String(128), nullable=False, default="")
    target_scope = Column(String(64), nullable=False, default="household", index=True)
    category = Column(String(64), nullable=False, default="milestone", index=True)
    summary = Column(Text, nullable=False)
    details_json = Column(JSON, default=dict, nullable=False)
    expires_at = Column(DateTime(timezone=True), nullable=True, index=True)
    source_session_id = Column(String(36), ForeignKey("conversation_sessions.id", ondelete="SET NULL"), nullable=True)
    is_active = Column(Boolean, default=True, nullable=False, index=True)
    created_at = Column(DateTime(timezone=True), default=get_utc_now, nullable=False)
    updated_at = Column(DateTime(timezone=True), default=get_utc_now, onupdate=get_utc_now, nullable=False)

    # Relationships
    source_user = relationship("UserModel")
    reporting_agent = relationship("AgentModel")
    source_session = relationship("SessionModel")
