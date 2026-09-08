from datetime import datetime
from typing import Optional, Dict, Any
from pydantic import BaseModel, ConfigDict, Field


class GossipMilestoneResponse(BaseModel):
    id: str
    source_user_id: str
    source_username: str
    reporting_agent_id: Optional[str] = None
    reporting_agent_name: str
    target_scope: str = "household"
    category: str
    summary: str
    details_json: Dict[str, Any] = {}
    expires_at: Optional[datetime] = None
    source_session_id: Optional[str] = None
    is_active: bool = True
    created_at: datetime
    updated_at: datetime

    model_config = ConfigDict(from_attributes=True)


class GossipMilestoneCreateRequest(BaseModel):
    category: str = Field(default="milestone", min_length=1, max_length=50)
    summary: str = Field(..., min_length=1, max_length=1000)
    target_scope: str = Field(default="household", max_length=50)
    details_json: Optional[Dict[str, Any]] = None
    expires_at: Optional[datetime] = None
