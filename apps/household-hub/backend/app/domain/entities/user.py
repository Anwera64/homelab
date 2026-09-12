from dataclasses import dataclass, field
from datetime import datetime, timezone
import uuid

# The first swatch on the first-run colour picker.
DEFAULT_AVATAR_COLOR = "#3C6E4E"


def get_utc_now() -> datetime:
    return datetime.now(timezone.utc)


@dataclass
class User:
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    full_name: str = ""
    hashed_pin: str = ""
    avatar_color: str = DEFAULT_AVATAR_COLOR
    is_admin: bool = False
    is_active: bool = True
    failed_pin_attempts: int = 0
    pin_locked_until: datetime | None = None
    personal_space_id: str | None = None
    created_at: datetime = field(default_factory=get_utc_now)
    updated_at: datetime = field(default_factory=get_utc_now)
