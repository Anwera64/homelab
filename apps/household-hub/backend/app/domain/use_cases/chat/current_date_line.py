from datetime import datetime, timezone, tzinfo
from typing import Optional
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

# Spelled out rather than taken from strftime, whose names follow the host's locale.
_WEEKDAYS = ("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
_MONTHS = (
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

# The longest IANA names are around 30 characters; anything far past that is not one.
_MAX_TIMEZONE_NAME = 64


def _zone(timezone_name: Optional[str]) -> Optional[tzinfo]:
    if not timezone_name or len(timezone_name) > _MAX_TIMEZONE_NAME:
        return None
    try:
        return ZoneInfo(timezone_name)
    except (ZoneInfoNotFoundError, ValueError):
        return None


def current_date_line(now: datetime, timezone_name: Optional[str]) -> str:
    """
    Today's date and time as the phone's owner would read it, for the top of a prompt.

    Without it the model answers from its training cutoff (#39). The timezone is the one the phone
    sent; one the hub can't resolve becomes UTC, and the line says so rather than passing UTC off
    as local time. Minutes, never seconds: the prompt stays the same for a whole minute.
    """
    zone = _zone(timezone_name)
    label = timezone_name if zone else "UTC"
    local = now.astimezone(zone or timezone.utc)
    return (
        f"Today is {_WEEKDAYS[local.weekday()]}, {local.day} {_MONTHS[local.month - 1]} {local.year}, "
        f"{local:%H:%M} ({label})."
    )
