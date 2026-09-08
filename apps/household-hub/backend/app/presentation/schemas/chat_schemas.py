from typing import Optional, List, Dict, Any
from pydantic import BaseModel, Field
from app.presentation.schemas.session_schemas import ChatMessageRead


class ChatTurnRequest(BaseModel):
    content: str = Field(..., min_length=1, max_length=65536)
    is_secret: bool = False
    auto_approve_writes: bool = False


class ChatTurnResponse(BaseModel):
    message: ChatMessageRead
    tool_calls: List[Dict[str, Any]] = []
    memories_created_count: int = 0
    milestones_created_count: int = 0
    suggest_secret_mode: bool = False
    session_title: Optional[str] = None


class ToolApprovalRequest(BaseModel):
    tool_call_id: str
    approved: bool
    modified_arguments: Optional[Dict[str, Any]] = None


class ToolApprovalResponse(BaseModel):
    status: str
    tool_call_id: str
    result: Optional[Any] = None
