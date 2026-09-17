from datetime import datetime
from typing import Optional
from pydantic import BaseModel, ConfigDict

from app.presentation.schemas.auth_schemas import Pin
from app.presentation.schemas.user_schemas import AvatarColor, FullName


class InviteCreate(BaseModel):
    invited_name: FullName
    is_admin: bool = False


class InviteRead(BaseModel):
    code: str
    invited_name: str
    is_admin: bool
    expires_at: datetime
    # The phone counts down from this rather than from expires_at: its clock may differ from the hub's.
    expires_in_seconds: int

    model_config = ConfigDict(from_attributes=True)


class InvitePreviewRead(BaseModel):
    invited_name: str
    inviter_name: str
    inviter_avatar_color: str


class InviteRedeem(BaseModel):
    full_name: FullName
    pin: Pin
    avatar_color: Optional[AvatarColor] = None
