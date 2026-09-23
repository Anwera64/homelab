import uuid
from datetime import datetime, timezone
from sqlalchemy import Column, String, Text, Float, Boolean, DateTime, ForeignKey, JSON
from sqlalchemy.orm import relationship

from app.data.models.base import Base


def get_utc_now():
    return datetime.now(timezone.utc)


class AgentModel(Base):
    __tablename__ = "agent_personalities"
    __table_args__ = {"extend_existing": True}

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    slug = Column(String(64), unique=True, index=True, nullable=False)
    name = Column(String(128), nullable=False)
    description = Column(Text, nullable=False, default="")
    avatar = Column(String(64), nullable=False, default="🤖")
    system_prompt = Column(Text, nullable=False)
    llm_model_id = Column(String(36), ForeignKey("llm_models.id", ondelete="SET NULL"), nullable=True)
    temperature = Column(Float, nullable=False, default=0.7)
    top_p = Column(Float, nullable=False, default=0.9)
    tool_permissions = Column(JSON, nullable=False, default=list)
    owner_id = Column(String(36), ForeignKey("users.id", ondelete="SET NULL"), nullable=True)
    is_builtin = Column(Boolean, nullable=False, default=False)
    is_active = Column(Boolean, nullable=False, default=True)
    deleted_at = Column(DateTime(timezone=True), nullable=True)
    created_at = Column(DateTime(timezone=True), default=get_utc_now, nullable=False)
    updated_at = Column(DateTime(timezone=True), default=get_utc_now, onupdate=get_utc_now, nullable=False)

    # Relationships
    owner = relationship("UserModel", back_populates="owned_agents")
    sessions = relationship("SessionModel", back_populates="agent")
