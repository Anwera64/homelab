from typing import Annotated, Optional
from pydantic import BaseModel, Field
from app.presentation.schemas.user_schemas import AvatarColor, FullName, UserRead

# [0-9], not \d: the regex engine behind pydantic counts other scripts' digits as \d.
Pin = Annotated[str, Field(pattern=r"^[0-9]{6}$")]


class LoginRequest(BaseModel):
    user_id: str = Field(..., min_length=1, max_length=36)
    pin: Pin


class FirstRunRegister(BaseModel):
    full_name: FullName
    pin: Pin
    avatar_color: Optional[AvatarColor] = None


class MemberProfile(BaseModel):
    """What the profile picker shows before anyone signs in."""
    id: str
    full_name: str
    avatar_color: str


class Token(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserRead


class AuthStatus(BaseModel):
    is_initialized: bool
    member_count: int
