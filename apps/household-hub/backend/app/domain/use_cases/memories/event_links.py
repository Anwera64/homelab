from datetime import datetime, time, timedelta, timezone
from typing import List, Optional

from app.domain.entities.calendar_event import CalendarEvent
from app.domain.entities.session import ChatMessage

CALENDAR_WRITE = "calendar_write"


def touched_event_ids(messages: List[ChatMessage]) -> List[str]:
    """
    The events a chat created or changed, from the calendar steps saved on its answers, in the order
    first touched (memory spec §7). One removed later in the same chat is left out, unless only one
    date of it went: the series is still there. Failed steps and cards still waiting changed nothing.
    """
    touched: List[str] = []
    for message in messages:
        if message.role != "assistant":
            continue
        for step in (message.metadata_json or {}).get("tools_executed") or []:
            if not isinstance(step, dict) or step.get("tool") != CALENDAR_WRITE or not step.get("success"):
                continue
            data = step.get("data") if isinstance(step.get("data"), dict) else {}
            event_id, action = data.get("event_id"), data.get("action")
            if not event_id:
                continue
            if action == "deleted":
                if not (step.get("arguments") or {}).get("occurrence_start") and event_id in touched:
                    touched.remove(event_id)
            elif action in ("created", "updated") and event_id not in touched:
                touched.append(event_id)
    return touched


def event_expiry(event: CalendarEvent) -> Optional[datetime]:
    """
    When a memory of [event] stops mattering: when it ends, or for a repeating one when the series
    ends, the day after its last date. A series with no end date, or one that ends after a count,
    never expires (memory spec §7).
    """
    if event.repeat is None:
        return event.end_time
    if event.repeat.until:
        return datetime.combine(event.repeat.until + timedelta(days=1), time.min, tzinfo=timezone.utc)
    return None
