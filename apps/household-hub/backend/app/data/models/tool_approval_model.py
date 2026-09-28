from datetime import datetime, timezone
from sqlalchemy import Column, String, Boolean, DateTime, ForeignKey
from app.data.models.base import Base


def get_utc_now():
    return datetime.now(timezone.utc)


class ToolApprovalModel(Base):
    """One member's choice for one write action: whether agents may do it without asking."""

    __tablename__ = "tool_approval_preferences"
    __table_args__ = {"extend_existing": True}

    user_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    tool = Column(String(64), primary_key=True)
    action = Column(String(32), primary_key=True)
    auto = Column(Boolean, nullable=False)
    updated_at = Column(DateTime(timezone=True), default=get_utc_now, onupdate=get_utc_now, nullable=False)
