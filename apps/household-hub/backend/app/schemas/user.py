from datetime import datetime
from typing import Optional
from pydantic import BaseModel, ConfigDict, Field


class UserBase(BaseModel):
    username: str
    email: str
    full_name: str
    avatar_color: Optional[str] = "#4F46E5"


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
    full_name: Optional[str] = None
    avatar_color: Optional[str] = None
    password: Optional[str] = Field(None, min_length=8, max_length=72)
