from sqlalchemy import Boolean, Column, ForeignKey, Integer, String
from app.data.models.base import Base


class CalendarSourceModel(Base):
    """One calendar of a member's account, and whether the hub reads it (#252)."""

    __tablename__ = "calendar_sources"
    __table_args__ = {"extend_existing": True}

    credential_id = Column(
        String(36), ForeignKey("calendar_credentials.id", ondelete="CASCADE"), primary_key=True
    )
    key = Column(String(16), primary_key=True)
    url = Column(String(1024), nullable=False)
    name = Column(String(255), nullable=False)
    writable = Column(Boolean, nullable=False)
    picked = Column(Boolean, nullable=False)
    # The account's own order, so the picker lists them as the calendar app does.
    position = Column(Integer, nullable=False)
