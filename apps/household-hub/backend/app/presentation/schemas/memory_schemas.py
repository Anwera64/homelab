from datetime import datetime
from typing import List, Optional
from pydantic import BaseModel, ConfigDict, Field


class MemoryBase(BaseModel):
    content: str = Field(..., min_length=1)
    category: Optional[str] = "fact"
    scope: Optional[str] = "personal"
    confidence: Optional[float] = Field(1.0, ge=0.0, le=1.0)
    agent_id: Optional[str] = None
    source_session_id: Optional[str] = None


class MemoryCreate(MemoryBase):
    pass


class MemoryUpdate(BaseModel):
    content: Optional[str] = None
    category: Optional[str] = None
    confidence: Optional[float] = None
    is_active: Optional[bool] = None


class MemoryRead(MemoryBase):
    id: str
    user_id: str
    is_active: bool
    created_at: datetime
    updated_at: datetime

    model_config = ConfigDict(from_attributes=True)


class MemoryPageItemRead(BaseModel):
    """One row on the memory page."""

    id: str
    content: str
    scope: str
    origin: str
    created_at: datetime
    expires_at: Optional[datetime] = None
    speaker_id: str
    speaker_name: str
    is_mine: bool
    agent_name: str
    agent_avatar: str
    can_remove: bool


class MemoryPageRead(BaseModel):
    yours: List[MemoryPageItemRead]
    household: List[MemoryPageItemRead]
