from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Optional
import uuid

PASSWORD = "password"
OAUTH = "oauth"


def get_utc_now() -> datetime:
    return datetime.now(timezone.utc)


@dataclass
class CalendarCredential:
    user_id: str
    provider: str  # "caldav" | "google_caldav" | "apple_icloud"
    url: str
    username: str
    # The password, or for an OAuth calendar the current access token.
    encrypted_secret: str
    calendar_name: str = "Default"
    is_active: bool = True
    auth_kind: str = PASSWORD  # PASSWORD | OAUTH
    encrypted_refresh_token: Optional[str] = None
    token_expires_at: Optional[datetime] = None
    # Google stopped honouring the sign-in; the member has to sign in again.
    needs_reconnect: bool = False
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    created_at: datetime = field(default_factory=get_utc_now)
    updated_at: datetime = field(default_factory=get_utc_now)
