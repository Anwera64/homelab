from datetime import datetime, timezone
from sqlalchemy import Column, String, Text, DateTime
from app.data.models.base import Base


def get_utc_now():
    return datetime.now(timezone.utc)


class SystemSettingModel(Base):
    __tablename__ = "system_settings"
    __table_args__ = {"extend_existing": True}

    key = Column(String(64), primary_key=True)
    value = Column(Text, nullable=False)
    created_at = Column(DateTime(timezone=True), default=get_utc_now, nullable=False)
    updated_at = Column(DateTime(timezone=True), default=get_utc_now, onupdate=get_utc_now, nullable=False)
