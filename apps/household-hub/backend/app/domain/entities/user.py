from dataclasses import dataclass, field
from datetime import datetime, timezone
import uuid


def get_utc_now() -> datetime:
    return datetime.now(timezone.utc)


@dataclass
class User:
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    username: str = ""
    email: str = ""
    full_name: str = ""
    hashed_password: str = ""
    avatar_color: str = "#4F46E5"
    is_admin: bool = False
    is_active: bool = True
    personal_space_id: str | None = None
    created_at: datetime = field(default_factory=get_utc_now)
    updated_at: datetime = field(default_factory=get_utc_now)
