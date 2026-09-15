import uuid
from datetime import datetime, timezone
from sqlalchemy import Column, String, Boolean, DateTime, Integer
from sqlalchemy.orm import relationship

from app.data.models.base import Base
from app.domain.entities.user import DEFAULT_AVATAR_COLOR


def get_utc_now():
    return datetime.now(timezone.utc)


class UserModel(Base):
    __tablename__ = "users"
    __table_args__ = {"extend_existing": True}

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    full_name = Column(String(128), nullable=False)
    hashed_pin = Column(String(255), nullable=False)
    avatar_color = Column(String(32), default=DEFAULT_AVATAR_COLOR, nullable=False)
    is_admin = Column(Boolean, default=False, nullable=False)
    is_active = Column(Boolean, default=True, nullable=False)
    failed_pin_attempts = Column(Integer, default=0, nullable=False)
    pin_locked_until = Column(DateTime(timezone=True), nullable=True)
    token_version = Column(Integer, default=0, nullable=False)
    created_at = Column(DateTime(timezone=True), default=get_utc_now, nullable=False)
    updated_at = Column(DateTime(timezone=True), default=get_utc_now, onupdate=get_utc_now, nullable=False)

    # Relationships
    personal_space = relationship("SpaceModel", back_populates="owner", uselist=False, cascade="all, delete-orphan")
    owned_agents = relationship("AgentModel", back_populates="owner")
    sessions = relationship("SessionModel", back_populates="user", cascade="all, delete-orphan")
    memories = relationship("MemoryModel", back_populates="user")
