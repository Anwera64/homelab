from pydantic import BaseModel, Field


class ToolApprovalRead(BaseModel):
    tool: str
    action: str
    # Agents do this for the member without showing a card.
    auto: bool
    # Removing an event and replacing a note: never automatic, whatever is sent.
    always_asks: bool


class ToolApprovalUpdate(BaseModel):
    tool: str = Field(..., min_length=1, max_length=64)
    action: str = Field(..., min_length=1, max_length=32)
    auto: bool
