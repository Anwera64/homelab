from datetime import date, datetime, tzinfo
from typing import Any, Optional

from app.domain.entities.calendar_event import FREQUENCIES, ONLY_THIS, SCOPES, WEEKDAYS, Repeat
from app.domain.use_cases.chat.current_date_line import zone_of

__all__ = ["CalendarArgumentError", "moment", "repeat", "scope", "zone_of"]


class CalendarArgumentError(ValueError):
    """An argument calendar_write can't use. Its message is written for the model, to fix and retry."""


def moment(value: Any, name: str, zone: Optional[tzinfo] = None) -> Optional[datetime]:
    """
    A time the model wrote. With the member's [zone] it is that clock time in the zone, whatever
    offset the model attached: a model writes "15:00Z" meaning 15:00, the approval card shows 15:00,
    and the member approves 15:00. The zone is a named one, so a repeating event follows its clock
    changes. Without a zone the time is taken as written.
    """
    if not value:
        return None
    try:
        written = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    except ValueError:
        raise CalendarArgumentError(f"'{name}' must be an ISO 8601 timestamp, not {value!r}.")
    return written if zone is None else written.replace(tzinfo=zone)


def scope(value: Any) -> str:
    """Which dates of a repeating event a change is for. Left out, it is the one date: the safer guess."""
    if value in (None, ""):
        return ONLY_THIS
    if value not in SCOPES:
        raise CalendarArgumentError(f"'scope' must be one of {', '.join(SCOPES)}, not {value!r}.")
    return value


def repeat(value: Any) -> Optional[Repeat]:
    """calendar_write's 'repeat' as a Repeat; None when it was left out or says the event doesn't repeat."""
    if value in (None, "", {}):
        return None
    if not isinstance(value, dict):
        raise CalendarArgumentError("'repeat' must be an object with at least a 'frequency'.")
    frequency = str(value.get("frequency") or "").lower()
    if frequency in ("", "none"):
        return None
    if frequency not in FREQUENCIES:
        raise CalendarArgumentError(f"'repeat.frequency' must be one of {', '.join(FREQUENCIES)}, not {frequency!r}.")

    try:
        interval = int(value["interval"]) if value.get("interval") not in (None, "") else 1
    except (TypeError, ValueError):
        raise CalendarArgumentError("'repeat.interval' must be a whole number.")
    if interval < 1:
        raise CalendarArgumentError("'repeat.interval' must be 1 or more.")

    days = [str(day).upper()[:2] for day in value.get("days") or []]
    if any(day not in WEEKDAYS for day in days):
        raise CalendarArgumentError(f"'repeat.days' must use {', '.join(WEEKDAYS)}.")
    if days and frequency != "weekly":
        raise CalendarArgumentError("'repeat.days' only goes with a weekly repeat.")

    until = None
    if value.get("until"):
        try:
            until = date.fromisoformat(str(value["until"])[:10])
        except ValueError:
            raise CalendarArgumentError(f"'repeat.until' must be a date like 2026-12-24, not {value['until']!r}.")

    count = None
    if value.get("count") not in (None, ""):
        try:
            count = int(value["count"])
        except (TypeError, ValueError):
            raise CalendarArgumentError("'repeat.count' must be a whole number.")
        if count < 1:
            raise CalendarArgumentError("'repeat.count' must be 1 or more.")
    if until and count:
        raise CalendarArgumentError("Give 'repeat.until' or 'repeat.count', not both.")

    return Repeat(frequency=frequency, interval=interval, days=days, until=until, count=count)
