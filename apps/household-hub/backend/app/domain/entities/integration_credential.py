from dataclasses import dataclass, field
from datetime import datetime, timezone
import uuid


def get_utc_now() -> datetime:
    return datetime.now(timezone.utc)


@dataclass
class CalendarCredential:
    user_id: str
    provider: str  # "caldav" | "google_caldav" | "apple_icloud"
    url: str
    username: str
    encrypted_secret: str
    calendar_name: str = "Default"
    is_active: bool = True
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    created_at: datetime = field(default_factory=get_utc_now)
    updated_at: datetime = field(default_factory=get_utc_now)
