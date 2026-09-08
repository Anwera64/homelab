from typing import Optional
from pydantic import BaseModel, Field
from app.schemas.user import UserRead


class LoginRequest(BaseModel):
    username: str
    password: str = Field(..., max_length=72)


class FirstRunRegister(BaseModel):
    username: str
    email: str
    password: str = Field(..., min_length=8, max_length=72)
    full_name: str
    avatar_color: Optional[str] = "#4F46E5"


class Token(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserRead


class AuthStatus(BaseModel):
    is_initialized: bool
    member_count: int
