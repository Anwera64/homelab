import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Optional


def get_utc_now() -> datetime:
    return datetime.now(timezone.utc)


@dataclass
class Invite:
    """
    A one-time code an admin gives someone joining the household. Whether they join as an admin is
    decided here, when the admin creates it — never by whoever redeems it.
    """
    code: str
    invited_name: str
    inviter_id: str
    expires_at: datetime
    is_admin: bool = False
    used_at: Optional[datetime] = None
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    created_at: datetime = field(default_factory=get_utc_now)

    def is_usable_at(self, now: datetime) -> bool:
        return self.used_at is None and now < self.expires_at


@dataclass
class InvitePreview:
    """What the joiner sees before choosing a PIN: who they're joining as, and who asked them."""
    invited_name: str
    inviter_name: str
    inviter_avatar_color: str
