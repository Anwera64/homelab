from datetime import datetime
from pydantic import BaseModel

from app.presentation.schemas.auth_schemas import Pin


class PinResetApprove(BaseModel):
    """The approver's own PIN, not the target's."""
    pin: Pin


class PinResetRead(BaseModel):
    code: str
    expires_at: datetime
    # The phone counts down from this rather than from expires_at: its clock may differ from the hub's.
    expires_in_seconds: int


class PinResetRedeem(BaseModel):
    pin: Pin
