from datetime import datetime
from typing import Optional, List, Dict, Any, Literal
from pydantic import BaseModel, ConfigDict, Field


class ChatMessageBase(BaseModel):
    role: Literal["user", "assistant", "system", "tool"]
    content: str = Field(default="", min_length=0, max_length=65536)
    metadata_json: Optional[Dict[str, Any]] = {}


class ChatMessageCreate(ChatMessageBase):
    content: str = Field(..., min_length=1, max_length=65536)



class ChatMessageRead(ChatMessageBase):
    id: str
    session_id: str
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)


class SessionBase(BaseModel):
    agent_id: Optional[str] = None
    title: Optional[str] = "New Conversation"
    is_secret: Optional[bool] = False


class SessionCreate(BaseModel):
    agent_id: str
    title: Optional[str] = "New Conversation"
    is_secret: Optional[bool] = False


class SessionSecretToggle(BaseModel):
    is_secret: bool


class SessionRead(SessionBase):
    id: str
    user_id: str
    is_archived: bool = False
    created_at: datetime
    updated_at: datetime

    model_config = ConfigDict(from_attributes=True)


class SessionDetailRead(SessionRead):
    messages: List[ChatMessageRead] = []
