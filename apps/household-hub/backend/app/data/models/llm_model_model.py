import uuid
from datetime import datetime, timezone
from sqlalchemy import Column, String, Boolean, DateTime

from app.data.models.base import Base


def get_utc_now():
    return datetime.now(timezone.utc)


class LLMModelModel(Base):
    __tablename__ = "llm_models"
    __table_args__ = {"extend_existing": True}

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    provider_model = Column(String(128), unique=True, nullable=False)
    display_name = Column(String(128), nullable=False, default="")
    is_default = Column(Boolean, nullable=False, default=False)
    created_at = Column(DateTime(timezone=True), default=get_utc_now, nullable=False)
    updated_at = Column(DateTime(timezone=True), default=get_utc_now, onupdate=get_utc_now, nullable=False)
