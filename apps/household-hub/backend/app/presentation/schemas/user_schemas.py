from datetime import datetime
from typing import Optional
from pydantic import BaseModel, ConfigDict, Field

USERNAME_REGEX = r"^[a-zA-Z0-9_.-]+$"
EMAIL_REGEX = r"^[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+$"


class UserBase(BaseModel):
    username: str = Field(..., min_length=3, max_length=64, pattern=USERNAME_REGEX)
    email: str = Field(..., min_length=5, max_length=255, pattern=EMAIL_REGEX)
    full_name: str = Field(..., min_length=1, max_length=128)
    avatar_color: Optional[str] = Field("#4F46E5", min_length=4, max_length=32)


class UserCreate(UserBase):
    password: str = Field(..., min_length=8, max_length=72)
    is_admin: Optional[bool] = False


class UserRead(UserBase):
    id: str
    is_admin: bool
    is_active: bool
    personal_space_id: Optional[str] = None
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)


class UserUpdate(BaseModel):
    full_name: Optional[str] = Field(None, min_length=1, max_length=128)
    avatar_color: Optional[str] = Field(None, min_length=4, max_length=32)
    password: Optional[str] = Field(None, min_length=8, max_length=72)
