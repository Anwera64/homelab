import uuid
from datetime import datetime, timezone
from sqlalchemy import Column, String, Boolean, DateTime, ForeignKey

from app.data.models.base import Base


def get_utc_now():
    return datetime.now(timezone.utc)


class InviteModel(Base):
    __tablename__ = "invites"
    __table_args__ = {"extend_existing": True}

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    code = Column(String(6), unique=True, index=True, nullable=False)
    invited_name = Column(String(128), nullable=False)
    inviter_id = Column(String(36), ForeignKey("users.id", ondelete="SET NULL"), nullable=True)
    is_admin = Column(Boolean, default=False, nullable=False)
    expires_at = Column(DateTime(timezone=True), nullable=False)
    used_at = Column(DateTime(timezone=True), nullable=True)
    created_at = Column(DateTime(timezone=True), default=get_utc_now, nullable=False)
