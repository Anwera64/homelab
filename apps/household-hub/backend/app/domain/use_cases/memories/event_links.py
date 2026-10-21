from dataclasses import dataclass
from datetime import datetime, time, timedelta, timezone
from typing import Dict, Iterator, List, Optional, Tuple

from app.domain.entities.calendar_event import CalendarEvent
from app.domain.entities.memory import AgentMemory
from app.domain.entities.session import ChatMessage

CALENDAR_WRITE = "calendar_write"


@dataclass(frozen=True)
class RemovedEvent:
    """An event a chat named that is gone: its title and start as the agent wrote them, in the member's time."""

    title: str
    start: str
    all_day: bool = False


def _calendar_steps(messages: List[ChatMessage]) -> Iterator[Tuple[str, str, dict]]:
    """Each calendar step that ran, in order, as (event id, action, arguments). Failed steps and cards still waiting changed nothing."""
    for message in messages:
        if message.role != "assistant":
            continue
        for step in (message.metadata_json or {}).get("tools_executed") or []:
            if not isinstance(step, dict) or step.get("tool") != CALENDAR_WRITE or not step.get("success"):
                continue
            data = step.get("data") if isinstance(step.get("data"), dict) else {}
            if data.get("event_id"):
                yield data["event_id"], data.get("action"), step.get("arguments") or {}


def event_calendars(messages: List[ChatMessage]) -> Dict[str, str]:
    """The calendar key of each event a chat's calendar steps named one for, the latest winning."""
    calendars: Dict[str, str] = {}
    for message in messages:
        if message.role != "assistant":
            continue
        for step in (message.metadata_json or {}).get("tools_executed") or []:
            if not isinstance(step, dict) or step.get("tool") != CALENDAR_WRITE or not step.get("success"):
                continue
            data = step.get("data") if isinstance(step.get("data"), dict) else {}
            calendar = data.get("calendar") or (step.get("arguments") or {}).get("calendar")
            if data.get("event_id") and calendar:
                calendars[data["event_id"]] = str(calendar)
    return calendars


def _removes_whole_event(action: Optional[str], arguments: dict) -> bool:
    """A delete without a date takes the whole event; with one, the series is still there."""
    return action == "deleted" and not arguments.get("occurrence_start")


def touched_event_ids(messages: List[ChatMessage]) -> List[str]:
    """
    The events a chat created or changed, from the calendar steps saved on its answers, in the order
    first touched (memory spec §7). One removed later in the same chat is left out, unless only one
    date of it went: the series is still there.
    """
    touched: List[str] = []
    for event_id, action, arguments in _calendar_steps(messages):
        if _removes_whole_event(action, arguments):
            if event_id in touched:
                touched.remove(event_id)
        elif action in ("created", "updated") and event_id not in touched:
            touched.append(event_id)
    return touched


def removed_events(messages: List[ChatMessage], gone_ids: List[str]) -> List[RemovedEvent]:
    """
    The events a chat named that no longer happen: removed in the chat itself, or [gone_ids], which
    the calendar no longer has. Each is named by its latest title and start in the chat; one the chat
    never gave a title is left out, as there is nothing to call it (memory spec §7).
    """
    named: Dict[str, RemovedEvent] = {}
    removed: List[str] = []
    for event_id, action, arguments in _calendar_steps(messages):
        if action in ("created", "updated") and arguments.get("title") and arguments.get("start_time"):
            named[event_id] = RemovedEvent(
                title=str(arguments["title"]),
                start=str(arguments["start_time"]),
                all_day=bool(arguments.get("is_all_day")),
            )
        elif _removes_whole_event(action, arguments) and event_id not in removed:
            removed.append(event_id)
    removed += [event_id for event_id in gone_ids if event_id not in removed]
    return [named[event_id] for event_id in removed if event_id in named]


def carry_event(new: AgentMemory, stored: AgentMemory, link: bool) -> None:
    """
    What [new] keeps from the event-linked [stored] it replaces or sits beside: the expiry when it has
    none of its own, and with [link] the event itself, so a later move or removal still reaches it.
    The words are the member's own and carry their own time, so nothing about a rebuilt sentence is
    copied (memory spec §7).
    """
    if stored.event_id is None:
        return
    if new.expires_at is None:
        new.expires_at = stored.expires_at
    if link:
        new.event_id = stored.event_id
        new.event_calendar = stored.event_calendar
        new.event_starts_at = stored.event_starts_at


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
