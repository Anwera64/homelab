from datetime import datetime
from typing import Optional
from pydantic import BaseModel, ConfigDict


class MemoryBase(BaseModel):
    content: str
    category: Optional[str] = "fact"
    scope: Optional[str] = "personal"
    confidence: Optional[float] = 1.0
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
