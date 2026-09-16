import uuid
from datetime import datetime, timezone
from sqlalchemy import Column, String, DateTime, ForeignKey

from app.data.models.base import Base


def get_utc_now():
    return datetime.now(timezone.utc)


class PinResetModel(Base):
    __tablename__ = "pin_resets"
    __table_args__ = {"extend_existing": True}

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    code = Column(String(6), unique=True, index=True, nullable=False)
    target_user_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=False)
    # Null when the hub itself issued the code, from the command line.
    approver_id = Column(String(36), ForeignKey("users.id", ondelete="SET NULL"), nullable=True)
    expires_at = Column(DateTime(timezone=True), nullable=False)
    used_at = Column(DateTime(timezone=True), nullable=True)
    created_at = Column(DateTime(timezone=True), default=get_utc_now, nullable=False)
