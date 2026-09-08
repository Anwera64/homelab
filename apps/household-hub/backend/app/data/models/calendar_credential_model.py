import uuid
from datetime import datetime, timezone
from sqlalchemy import Column, String, Boolean, DateTime, Text, ForeignKey
from app.data.models.base import Base


def get_utc_now():
    return datetime.now(timezone.utc)


class CalendarCredentialModel(Base):
    __tablename__ = "calendar_credentials"
    __table_args__ = {"extend_existing": True}

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), unique=True, index=True, nullable=False)
    provider = Column(String(64), nullable=False)  # "caldav" | "google_caldav" | "apple_icloud"
    url = Column(String(512), nullable=False)
    username = Column(String(255), nullable=False)
    encrypted_secret = Column(Text, nullable=False)
    calendar_name = Column(String(128), default="Default", nullable=False)
    is_active = Column(Boolean, default=True, nullable=False)
    created_at = Column(DateTime(timezone=True), default=get_utc_now, nullable=False)
    updated_at = Column(DateTime(timezone=True), default=get_utc_now, onupdate=get_utc_now, nullable=False)
