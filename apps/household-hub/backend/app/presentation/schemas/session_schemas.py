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

    # What a Chats row draws beside the title. Resolved by the hub so the list arrives complete:
    # the phone holds a uuid otherwise, and cannot turn it into an emoji and a name.
    last_message_preview: Optional[str] = None
    agent_name: Optional[str] = None
    agent_avatar: Optional[str] = None

    model_config = ConfigDict(from_attributes=True)


class SessionDetailRead(SessionRead):
    messages: List[ChatMessageRead] = []

    # Whether a turn is being generated for this conversation right now. The assistant's message
    # is only written once generation finishes, so without this a transcript ending in a question
    # is ambiguous: still being answered, or the turn died. Set by the router from the session
    # lock registry, which is presentation's own bookkeeping and no business of the entity.
    turn_running: bool = False
