from typing import Optional, List, Dict, Any
from pydantic import BaseModel, Field
from app.presentation.schemas.session_schemas import ChatMessageRead


class ChatTurnRequest(BaseModel):
    content: str = Field(..., min_length=1, max_length=65536)
    is_secret: bool = False


class ChatTurnResponse(BaseModel):
    message: ChatMessageRead
    tool_calls: List[Dict[str, Any]] = []
    memories_created_count: int = 0
    milestones_created_count: int = 0
    suggest_secret_mode: bool = False
    session_title: Optional[str] = None


class ToolDecisionRequest(BaseModel):
    approved: bool
    # The details the member changed on the card, over the ones the model proposed.
    modified_arguments: Optional[Dict[str, Any]] = None
