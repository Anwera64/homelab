import uuid
from datetime import datetime, timezone
from sqlalchemy import Column, String, DateTime, ForeignKey, JSON
from sqlalchemy.orm import relationship

from app.core.database import Base


def get_utc_now():
    return datetime.now(timezone.utc)


class Space(Base):
    __tablename__ = "spaces"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    name = Column(String(128), nullable=False)
    type = Column(String(32), nullable=False)  # 'personal' or 'shared'
    owner_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=True, unique=True)
    settings = Column(JSON, default=dict, nullable=False)
    created_at = Column(DateTime(timezone=True), default=get_utc_now, nullable=False)
    updated_at = Column(DateTime(timezone=True), default=get_utc_now, onupdate=get_utc_now, nullable=False)

    # Relationships
    owner = relationship("User", back_populates="personal_space")
