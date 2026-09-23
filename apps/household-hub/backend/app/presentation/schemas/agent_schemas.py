from datetime import datetime
from typing import Optional, List
from pydantic import BaseModel, ConfigDict, Field, field_validator

ALLOWED_TOOL_PERMISSIONS = {
    "calendar_read",
    "calendar_write",
    "searxng_search",
    "pdf_reader",
    "document_writer",
}


class AgentBase(BaseModel):
    name: str
    description: Optional[str] = ""
    avatar: Optional[str] = "🤖"
    system_prompt: str
    # None follows the household default model; an id pins the agent to that model.
    llm_model_id: Optional[str] = None
    temperature: Optional[float] = Field(0.7, ge=0.0, le=2.0)
    top_p: Optional[float] = Field(0.9, ge=0.0, le=1.0)
    tool_permissions: Optional[List[str]] = []

    @field_validator("tool_permissions")
    @classmethod
    def validate_tools(cls, v: Optional[List[str]]) -> Optional[List[str]]:
        if v is not None:
            for tool in v:
                if tool not in ALLOWED_TOOL_PERMISSIONS:
                    raise ValueError(f"Invalid tool permission '{tool}'. Allowed: {sorted(ALLOWED_TOOL_PERMISSIONS)}")
        return v


class AgentCreate(AgentBase):
    slug: str = Field(..., pattern=r"^[a-z0-9]+(?:[-_][a-z0-9]+)*$", min_length=2, max_length=64)


class AgentUpdate(BaseModel):
    name: Optional[str] = None
    description: Optional[str] = None
    avatar: Optional[str] = None
    system_prompt: Optional[str] = None
    llm_model_id: Optional[str] = None
    temperature: Optional[float] = Field(None, ge=0.0, le=2.0)
    top_p: Optional[float] = Field(None, ge=0.0, le=1.0)
    tool_permissions: Optional[List[str]] = None
    is_active: Optional[bool] = None

    @field_validator("tool_permissions")
    @classmethod
    def validate_tools(cls, v: Optional[List[str]]) -> Optional[List[str]]:
        if v is not None:
            for tool in v:
                if tool not in ALLOWED_TOOL_PERMISSIONS:
                    raise ValueError(f"Invalid tool permission '{tool}'. Allowed: {sorted(ALLOWED_TOOL_PERMISSIONS)}")
        return v


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
