from datetime import datetime
from typing import Optional, List, Dict, Any
from pydantic import BaseModel, ConfigDict


class ChatMessageBase(BaseModel):
    role: str
    content: str
    metadata_json: Optional[Dict[str, Any]] = {}


class ChatMessageCreate(ChatMessageBase):
    pass


class ChatMessageRead(ChatMessageBase):
    id: str
    session_id: str
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)


class SessionBase(BaseModel):
    agent_id: str
    title: Optional[str] = "New Conversation"
    is_secret: Optional[bool] = False


class SessionCreate(SessionBase):
    pass


class SessionSecretToggle(BaseModel):
    is_secret: bool


class SessionRead(SessionBase):
    id: str
    user_id: str
    created_at: datetime
    updated_at: datetime

    model_config = ConfigDict(from_attributes=True)


class SessionDetailRead(SessionRead):
    messages: List[ChatMessageRead] = []
