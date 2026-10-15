from datetime import datetime, timezone
from sqlalchemy import Column, String, DateTime, ForeignKey, LargeBinary

from app.data.models.base import Base


def get_utc_now():
    return datetime.now(timezone.utc)


class MemoryVectorModel(Base):
    """
    A memory's meaning as numbers (memory spec §4.1): one row for its statement, one for its
    `relevant_for` list. An ordinary table, so the vector goes when the memory does.
    """

    __tablename__ = "memory_vectors"
    __table_args__ = {"extend_existing": True}

    memory_id = Column(String(36), ForeignKey("agent_memories.id", ondelete="CASCADE"), primary_key=True)
    kind = Column(String(16), primary_key=True)  # 'content' or 'relevant_for'
    # Vectors from two models can't be compared, so a change of EMBEDDING_MODEL shows which to redo.
    model = Column(String(128), nullable=False, index=True)
    # Packed 32-bit floats, the layout sqlite-vec reads.
    vector = Column(LargeBinary, nullable=False)
    created_at = Column(DateTime(timezone=True), default=get_utc_now, nullable=False)
