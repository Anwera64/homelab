from typing import Optional
from pydantic import BaseModel
from app.schemas.user import UserRead


class LoginRequest(BaseModel):
    username: str
    password: str


class FirstRunRegister(BaseModel):
    username: str
    email: str
    password: str
    full_name: str
    avatar_color: Optional[str] = "#4F46E5"


class Token(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserRead


class AuthStatus(BaseModel):
    is_initialized: bool
    member_count: int
