from datetime import datetime
from typing import Annotated, Optional
from pydantic import BaseModel, ConfigDict, Field, StringConstraints

# A name is how a member shows on the profile picker, so blank after trimming doesn't count.
FullName = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=128)]
AvatarColor = Annotated[str, Field(min_length=4, max_length=32)]


class UserRead(BaseModel):
    id: str
    full_name: str
    avatar_color: str
    is_admin: bool
    is_active: bool
    personal_space_id: Optional[str] = None
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)


class UserUpdate(BaseModel):
    full_name: Optional[FullName] = None
    avatar_color: Optional[AvatarColor] = None
