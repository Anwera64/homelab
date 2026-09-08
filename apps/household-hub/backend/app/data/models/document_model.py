import uuid
from datetime import datetime, timezone
from sqlalchemy import Column, String, Integer, DateTime, Text, ForeignKey
from app.data.models.base import Base


def get_utc_now():
    return datetime.now(timezone.utc)


class DocumentModel(Base):
    __tablename__ = "app_documents"
    __table_args__ = {"extend_existing": True}

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=False)
    space_id = Column(String(36), ForeignKey("spaces.id", ondelete="SET NULL"), index=True, nullable=True)
    title = Column(String(255), index=True, nullable=False)
    content = Column(Text, nullable=False)
    format = Column(String(32), default="markdown", nullable=False)
    version = Column(Integer, default=1, nullable=False)
    created_at = Column(DateTime(timezone=True), default=get_utc_now, nullable=False)
    updated_at = Column(DateTime(timezone=True), default=get_utc_now, onupdate=get_utc_now, nullable=False)
