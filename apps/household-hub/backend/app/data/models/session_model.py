import uuid
from datetime import datetime, timezone
from sqlalchemy import Column, String, Text, Boolean, DateTime, ForeignKey, Index, JSON
from sqlalchemy.orm import relationship

from app.data.models.base import Base


def get_utc_now():
    return datetime.now(timezone.utc)


class SessionModel(Base):
    __tablename__ = "conversation_sessions"
    __table_args__ = {"extend_existing": True}

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    agent_id = Column(String(36), ForeignKey("agent_personalities.id", ondelete="SET NULL"), nullable=True)
    title = Column(String(255), nullable=False, default="New Conversation")
    is_secret = Column(Boolean, default=False, nullable=False)
    is_archived = Column(Boolean, default=False, nullable=False)
    created_at = Column(DateTime(timezone=True), default=get_utc_now, nullable=False)
    updated_at = Column(DateTime(timezone=True), default=get_utc_now, onupdate=get_utc_now, nullable=False)

    # The rolling summary of everything up to and including summarized_through_id. Written only by
    # save_history_summary's own UPDATE, never through the ORM object's normal save path, so a
    # session update elsewhere can't carry a stale copy back over it.
    history_summary = Column(Text, nullable=True)
    summarized_through_id = Column(String(36), nullable=True)

    # Relationships
    user = relationship("UserModel", back_populates="sessions")
    agent = relationship("AgentModel", back_populates="sessions")
    messages = relationship(
        "MessageModel",
        back_populates="session",
        cascade="all, delete-orphan",
        order_by="MessageModel.created_at.asc()",
    )


class MessageModel(Base):
    __tablename__ = "chat_messages"
    __table_args__ = (
        # SQLite does not index a foreign key on its own, so without this every read of a
        # conversation — and every preview on the Chats list — scans the whole message table.
        # Ordered by created_at as well as session_id so "the newest message here" is a seek.
        Index("ix_chat_messages_session_created", "session_id", "created_at"),
        {"extend_existing": True},
    )

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    session_id = Column(String(36), ForeignKey("conversation_sessions.id", ondelete="CASCADE"), nullable=False)
    role = Column(String(32), nullable=False)  # 'user', 'assistant', 'system'
    content = Column(Text, nullable=False)
    metadata_json = Column(JSON, default=dict, nullable=False)
    created_at = Column(DateTime(timezone=True), default=get_utc_now, nullable=False)

    # Relationships
    session = relationship("SessionModel", back_populates="messages")
