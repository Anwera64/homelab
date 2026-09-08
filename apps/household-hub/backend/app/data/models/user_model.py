import uuid
from datetime import datetime, timezone
from sqlalchemy import Column, String, Boolean, DateTime, JSON
from sqlalchemy.orm import relationship

from app.data.models.base import Base


def get_utc_now():
    return datetime.now(timezone.utc)


class UserModel(Base):
    __tablename__ = "users"
    __table_args__ = {"extend_existing": True}

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    username = Column(String(64), unique=True, index=True, nullable=False)
    email = Column(String(255), unique=True, index=True, nullable=False)
    full_name = Column(String(128), nullable=False)
    hashed_password = Column(String(255), nullable=False)
    avatar_color = Column(String(32), default="#4F46E5", nullable=False)
    is_admin = Column(Boolean, default=False, nullable=False)
    is_active = Column(Boolean, default=True, nullable=False)
    created_at = Column(DateTime(timezone=True), default=get_utc_now, nullable=False)
    updated_at = Column(DateTime(timezone=True), default=get_utc_now, onupdate=get_utc_now, nullable=False)

    # Relationships
    personal_space = relationship("SpaceModel", back_populates="owner", uselist=False, cascade="all, delete-orphan")
    owned_agents = relationship("AgentModel", back_populates="owner")
    sessions = relationship("SessionModel", back_populates="user", cascade="all, delete-orphan")
    memories = relationship("MemoryModel", back_populates="user")
