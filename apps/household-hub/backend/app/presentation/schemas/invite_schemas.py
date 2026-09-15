from datetime import datetime
from pydantic import BaseModel, ConfigDict

from app.presentation.schemas.user_schemas import FullName


class InviteCreate(BaseModel):
    invited_name: FullName
    is_admin: bool = False


class InviteRead(BaseModel):
    code: str
    invited_name: str
    is_admin: bool
    expires_at: datetime

    model_config = ConfigDict(from_attributes=True)
