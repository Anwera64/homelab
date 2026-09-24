from datetime import datetime, timezone, tzinfo
from typing import Optional, Tuple
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


def timezone_label(timezone_name: Optional[str]) -> str:
    """The zone times are shown in: the phone's, or UTC when it can't be used."""
    return timezone_name if _zone(timezone_name) else "UTC"


def _local(when: datetime, timezone_name: Optional[str]) -> Tuple[datetime, str]:
    """[when] in the phone's zone, and the name to show for it: UTC when the zone can't be used."""
    zone = _zone(timezone_name)
    # SQLite gives times back without their zone; they were all written in UTC.
    if when.tzinfo is None:
        when = when.replace(tzinfo=timezone.utc)
    return when.astimezone(zone or timezone.utc), timezone_label(timezone_name)


def current_date_line(now: datetime, timezone_name: Optional[str]) -> str:
    """
    Today's date and time as the phone's owner would read it, for the top of a prompt.

    Without it the model answers from its training cutoff (#39). The timezone is the one the phone
    sent; one the hub can't resolve becomes UTC, and the line says so rather than passing UTC off
    as local time. Minutes, never seconds: the prompt stays the same for a whole minute.
    """
    local, label = _local(now, timezone_name)
    return (
        f"Today is {_WEEKDAYS[local.weekday()]}, {local.day} {_MONTHS[local.month - 1]} {local.year}, "
        f"{local:%H:%M} ({label})."
    )


def sent_at_stamp(when: datetime, timezone_name: Optional[str]) -> str:
    """
    When a message was sent, short, for a transcript: "Mon 21 Sep 2026, 08:14".

    Lets a model reading old messages work out which day "tomorrow" meant to whoever wrote it.
    """
    local, _ = _local(when, timezone_name)
    return (
        f"{_WEEKDAYS[local.weekday()][:3]} {local.day} {_MONTHS[local.month - 1][:3]} {local.year}, "
        f"{local:%H:%M}"
    )
