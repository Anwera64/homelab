from datetime import datetime, timezone
from typing import Optional

from app.domain.entities.memory import ALL_DAY, AgentMemory
from app.domain.use_cases.chat.current_date_line import _MONTHS, _WEEKDAYS, zone_of


def event_sentence(title: str, starts_at: datetime, kind: str, timezone_name: Optional[str]) -> str:
    """
    A memory rebuilt from its event, as one reader sees it: "Dinner together on Sunday 19 October,
    19:30." in [timezone_name]. Without a zone the hub knows, the time is UTC and says so. An all-day
    event is a date, the same in every zone.
    """
    title = " ".join((title or "").split()).rstrip(".")
    start = starts_at if starts_at.tzinfo else starts_at.replace(tzinfo=timezone.utc)
    if kind == ALL_DAY:
        day = start.astimezone(timezone.utc)
        return f"{title} on {_WEEKDAYS[day.weekday()]} {day.day} {_MONTHS[day.month - 1]}."
    zone = zone_of(timezone_name)
    local = start.astimezone(zone or timezone.utc)
    clock = f"{local:%H:%M}" if zone else f"{local:%H:%M} UTC"
    return f"{title} on {_WEEKDAYS[local.weekday()]} {local.day} {_MONTHS[local.month - 1]}, {clock}."


def memory_text(memory: AgentMemory, timezone_name: Optional[str]) -> str:
    """What a memory says to its reader: its own words, or, once rebuilt from its event, the time added."""
    if memory.time_from_event and memory.event_starts_at:
        return event_sentence(memory.content, memory.event_starts_at, memory.time_from_event, timezone_name)
    return memory.content
