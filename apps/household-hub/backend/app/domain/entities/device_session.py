import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone


def get_utc_now() -> datetime:
    return datetime.now(timezone.utc)


@dataclass
class DeviceSession:
    """
    One device a member is signed in on. Its id is stamped into every token that device is given,
    as `sid`, and a refreshed token keeps it — so the tokens a device ever held stand or fall with
    this one row. Signing out removes it.

    Not a chat: that is `Session`.
    """
    user_id: str
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    created_at: datetime = field(default_factory=get_utc_now)
    # When the device last signed in or refreshed its token, which is when its newest token was made.
    last_seen_at: datetime = field(default_factory=get_utc_now)
