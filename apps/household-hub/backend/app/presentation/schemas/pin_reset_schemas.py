from datetime import datetime
from pydantic import BaseModel

from app.presentation.schemas.auth_schemas import Pin


class PinResetApprove(BaseModel):
    """The approver's own PIN, not the target's."""
    pin: Pin


class PinResetRead(BaseModel):
    code: str
    expires_at: datetime


class PinResetRedeem(BaseModel):
    pin: Pin
