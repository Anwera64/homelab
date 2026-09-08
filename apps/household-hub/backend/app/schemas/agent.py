from datetime import datetime
from typing import Optional, List
from pydantic import BaseModel, ConfigDict, Field


class AgentBase(BaseModel):
    name: str
    description: Optional[str] = ""
    avatar: Optional[str] = "🤖"
    system_prompt: str
    model_alias: Optional[str] = "qwen3:14b"
    temperature: Optional[float] = Field(0.7, ge=0.0, le=2.0)
    top_p: Optional[float] = Field(0.9, ge=0.0, le=1.0)
    tool_permissions: Optional[List[str]] = []


class AgentCreate(AgentBase):
    slug: str = Field(..., pattern=r"^[a-z0-9]+(?:[-_][a-z0-9]+)*$", min_length=2, max_length=64)


class AgentUpdate(BaseModel):
    name: Optional[str] = None
    description: Optional[str] = None
    avatar: Optional[str] = None
    system_prompt: Optional[str] = None
    model_alias: Optional[str] = None
    temperature: Optional[float] = Field(None, ge=0.0, le=2.0)
    top_p: Optional[float] = Field(None, ge=0.0, le=1.0)
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
