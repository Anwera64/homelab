from datetime import datetime
from typing import List, Literal, Optional
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


class WaitingChangeRead(BaseModel):
    """A sentence you asked to remember over someone else's memory, still waiting on their answer."""

    id: str
    content: str
    agent_name: str
    created_at: datetime
    expires_at: Optional[datetime] = None
    answerer_name: str
    stored: MemoryPageItemRead


class MemoryPageRead(BaseModel):
    yours: List[MemoryPageItemRead]
    household: List[MemoryPageItemRead]
    # The member's changes to check: the band on this page and the chip on Profile.
    to_check: int = 0
    waiting: List[WaitingChangeRead] = []


class ChangeSourceRead(BaseModel):
    """Where the new sentence was read. The chat's title is only for the chat's owner (memory spec §6.1)."""

    title: Optional[str] = None
    owner_name: str
    read_at: datetime


class ChangeCardRead(BaseModel):
    """One change to check: the stored memory beside the new sentence (memory spec §6.1)."""

    id: str
    content: str
    stored: MemoryPageItemRead
    can_answer: bool
    answerer_name: str
    source: ChangeSourceRead


class ChangesToCheckRead(BaseModel):
    to_check: List[ChangeCardRead]
    waiting: List[ChangeCardRead]


class ProposalAnswer(BaseModel):
    answer: Literal["keep_stored", "keep_both", "use_new"]


class ProposalRead(BaseModel):
    id: str
    memory_id: str
    content: str
    state: str
    new_memory_id: Optional[str] = None
    answered_at: Optional[datetime] = None


class LookedOverSummaryRead(BaseModel):
    """The quiet line after the last message looked over (memory spec §8)."""

    through_message_id: Optional[str] = None
    at: datetime
    remembered: int
    to_check: int


class LookedOverRead(BaseModel):
    """The sheet the line opens: "Remembered from this chat"."""

    at: datetime
    to_check: List[ChangeCardRead]
    waiting: List[ChangeCardRead]
    remembered: List[MemoryPageItemRead]
