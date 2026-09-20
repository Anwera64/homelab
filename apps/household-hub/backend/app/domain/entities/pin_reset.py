import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Optional


def get_utc_now() -> datetime:
    return datetime.now(timezone.utc)


@dataclass
class PinReset:
    """
    A one-time code that lets a member choose a new PIN. Another member approves it with their own
    PIN, or whoever can reach the hub issues one from the command line — that is the last resort,
    and it cannot be lost with a phone.
    """
    code: str
    target_user_id: str
    expires_at: datetime
    approver_id: Optional[str] = None
    used_at: Optional[datetime] = None
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    created_at: datetime = field(default_factory=get_utc_now)

    def is_usable_at(self, now: datetime) -> bool:
        return self.used_at is None and now < self.expires_at
