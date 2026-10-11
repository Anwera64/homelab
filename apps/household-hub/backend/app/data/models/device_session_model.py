import uuid
from datetime import datetime, timezone
from sqlalchemy import Column, String, DateTime, ForeignKey

from app.data.models.base import Base


def get_utc_now():
    return datetime.now(timezone.utc)


class DeviceSessionModel(Base):
    __tablename__ = "device_sessions"
    __table_args__ = {"extend_existing": True}

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=False)
    created_at = Column(DateTime(timezone=True), default=get_utc_now, nullable=False)
    last_seen_at = Column(DateTime(timezone=True), default=get_utc_now, nullable=False)
