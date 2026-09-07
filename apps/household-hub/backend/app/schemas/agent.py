from datetime import datetime
from typing import Optional, List
from pydantic import BaseModel, ConfigDict


class AgentBase(BaseModel):
    name: str
    description: Optional[str] = ""
    avatar: Optional[str] = "🤖"
    system_prompt: str
    model_alias: Optional[str] = "qwen3:14b"
    temperature: Optional[float] = 0.7
    top_p: Optional[float] = 0.9
    tool_permissions: Optional[List[str]] = []


class AgentCreate(AgentBase):
    slug: str


class AgentUpdate(BaseModel):
    name: Optional[str] = None
    description: Optional[str] = None
    avatar: Optional[str] = None
    system_prompt: Optional[str] = None
    model_alias: Optional[str] = None
    temperature: Optional[float] = None
    top_p: Optional[float] = None
    tool_permissions: Optional[List[str]] = None
    is_active: Optional[bool] = None


class AgentRead(AgentBase):
    id: str
    slug: str
    is_builtin: bool
    is_active: bool
    owner_id: Optional[str] = None
    deleted_at: Optional[datetime] = None
    created_at: datetime
    updated_at: datetime

    model_config = ConfigDict(from_attributes=True)


class AgentTrashRead(AgentRead):
    days_remaining_in_grace_period: int
