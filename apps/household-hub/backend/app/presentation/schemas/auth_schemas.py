from typing import Optional
from pydantic import BaseModel, Field
from app.presentation.schemas.user_schemas import UserRead, USERNAME_REGEX, EMAIL_REGEX


class LoginRequest(BaseModel):
    username: str
    password: str = Field(..., max_length=72)


class FirstRunRegister(BaseModel):
    username: str = Field(..., min_length=3, max_length=64, pattern=USERNAME_REGEX)
    email: str = Field(..., min_length=5, max_length=255, pattern=EMAIL_REGEX)
    password: str = Field(..., min_length=8, max_length=72)
    full_name: str = Field(..., min_length=1, max_length=128)
    avatar_color: Optional[str] = Field("#4F46E5", min_length=4, max_length=32)


class Token(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserRead


class AuthStatus(BaseModel):
    is_initialized: bool
    member_count: int
