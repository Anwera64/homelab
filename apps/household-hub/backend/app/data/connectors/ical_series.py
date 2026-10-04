"""
Repeating events in iCalendar terms (RFC 5545), kept apart from the CalDAV calls so they can be
tested on their own.

A repeating event is one calendar object holding a master VEVENT with an RRULE, plus one VEVENT per
date that was changed on its own, named by its RECURRENCE-ID. Every date of the series shares the
master's UID, so a date is only told apart by when it was due: its "occurrence".
"""

import copy
import uuid
from datetime import date, datetime, timedelta, timezone
from typing import Any, Dict, List, Optional

from dateutil.rrule import rrulestr
from icalendar import Calendar as ICalendar, Event as IEvent, vRecur

from app.domain.entities.calendar_event import FREQUENCIES, WEEKDAYS, Repeat


def repeat_rule(repeat: Repeat) -> Dict[str, Any]:
    """The RRULE for [repeat], as icalendar's `add` takes it."""
    rule: Dict[str, Any] = {"freq": repeat.frequency.upper()}
    if repeat.interval and repeat.interval > 1:
        rule["interval"] = repeat.interval
    if repeat.days:
        rule["byday"] = list(repeat.days)
    if repeat.count:
        rule["count"] = repeat.count
    elif repeat.until:
        rule["until"] = repeat.until
    return rule


def set_repeat(component: IEvent, repeat: Repeat) -> None:
    """Gives [component] the rule [repeat], replacing any it had. UNTIL takes DTSTART's form."""
    rule = repeat_rule(repeat)
    if "until" in rule:
        rule["until"] = _until(repeat.until, component.get("dtstart").dt)
    component.pop("rrule", None)
    component.add("rrule", rule)


def repeat_of(component: IEvent) -> Optional[Repeat]:
    """
    The rule [component] repeats by, in the tools' words. None when it doesn't repeat, or repeats in
    a way they have no words for (an hourly rule, "the second Tuesday"): those are read as one-offs.
    """
    rule = component.get("rrule")
    if isinstance(rule, list):
        rule = rule[0] if len(rule) == 1 else None
    if not rule:
        return None
    frequency = str(_first(rule.get("FREQ")) or "").lower()
    if frequency not in FREQUENCIES:
        return None
    days = []
    for day in rule.get("BYDAY") or []:
        day = str(day).upper()
        if day not in WEEKDAYS:
            return None
        days.append(day)
    until = _first(rule.get("UNTIL"))
    count = _first(rule.get("COUNT"))
    return Repeat(
        frequency=frequency,
        interval=int(_first(rule.get("INTERVAL")) or 1),
        days=days,
        until=until.date() if isinstance(until, datetime) else until,
        count=int(count) if count else None,
    )


def master(calendar: ICalendar) -> Optional[IEvent]:
    """The VEVENT that holds the series' rule: the one not named by a RECURRENCE-ID."""
    return next((c for c in calendar.walk("VEVENT") if "recurrence-id" not in c), None)


def repeats(calendar: ICalendar) -> bool:
    main = master(calendar)
    return main is not None and "rrule" in main


def occurrence_of(component: IEvent) -> datetime:
    """Which date of its series [component] is, as the phone and the model name it."""
    value = component.get("recurrence-id") or component.get("dtstart")
    return as_moment(value.dt)


def as_moment(value) -> datetime:
    """A DTSTART-like value as an aware datetime: a whole day is its midnight, floating time is UTC."""
    if not isinstance(value, datetime):
        return datetime.combine(value, datetime.min.time(), tzinfo=timezone.utc)
    if value.tzinfo is None:
        return value.replace(tzinfo=timezone.utc)
    return value


def like(moment: datetime, dtstart) -> Any:
    """[moment] in the form [dtstart] has, so it can be written beside it or compared with it."""
    if not isinstance(dtstart, datetime):
        return moment.date()
    if dtstart.tzinfo is None:
        if moment.tzinfo is None:
            return moment
        return moment.astimezone(timezone.utc).replace(tzinfo=None)
    if moment.tzinfo is None:
        return moment.replace(tzinfo=dtstart.tzinfo)
    return moment.astimezone(dtstart.tzinfo)


def same_occurrence(component: IEvent, occurrence: datetime) -> bool:
    recurrence_id = component.get("recurrence-id")
    if recurrence_id is None:
        return False
    return as_moment(recurrence_id.dt) == as_moment(like(occurrence, recurrence_id.dt))


def override(calendar: ICalendar, occurrence: datetime) -> IEvent:
    """
    The VEVENT for the one date [occurrence] of the series: the one already there when that date was
    changed before, else a copy of the master moved to that date, added to [calendar].
    """
    existing = next((c for c in calendar.walk("VEVENT") if same_occurrence(c, occurrence)), None)
    if existing is not None:
        return existing
    main = master(calendar)
    start = main.get("dtstart").dt
    at = like(occurrence, start)
    single = copy.deepcopy(main)
    for name in ("rrule", "rdate", "exdate", "dtstart", "dtend", "duration"):
        single.pop(name, None)
    single.add("recurrence-id", at)
    single.add("dtstart", at)
    single.add("dtend", at + duration(main))
    calendar.add_component(single)
    return single


def skip(calendar: ICalendar, occurrence: datetime) -> None:
    """Takes the one date [occurrence] out of the series; every other date stays."""
    main = master(calendar)
    main.add("exdate", like(occurrence, main.get("dtstart").dt))
    _drop(calendar, lambda c: same_occurrence(c, occurrence))


def is_first(calendar: ICalendar, occurrence: datetime) -> bool:
    """Whether [occurrence] is the series' first date (or earlier): from there on is all of it."""
    start = master(calendar).get("dtstart").dt
    return as_moment(like(occurrence, start)) <= as_moment(start)


def end_before(calendar: ICalendar, occurrence: datetime) -> bool:
    """
    Ends the series just before [occurrence], dropping the dates changed on their own from then on.
    False when nothing would be left, [occurrence] being its first date: the caller removes the whole
    event instead. Carry the series on with following() first, since this changes its rule.
    """
    if is_first(calendar, occurrence):
        return False
    main = master(calendar)
    start = main.get("dtstart").dt
    rule = dict(main.get("rrule"))
    rule.pop("COUNT", None)
    rule["UNTIL"] = [_until_before(occurrence, start)]
    main.pop("rrule", None)
    main.add("rrule", vRecur(rule))
    _drop(calendar, lambda c: "recurrence-id" in c and as_moment(c["recurrence-id"].dt) >= as_moment(like(occurrence, c["recurrence-id"].dt)))
    return True


def following(calendar: ICalendar, occurrence: datetime) -> ICalendar:
    """
    A new series, with its own UID, that carries [calendar]'s series on from [occurrence]: same
    event, same rule, and whatever of a COUNT is left. The caller ends the old one with end_before.
    """
    main = master(calendar)
    start = main.get("dtstart").dt
    at = like(occurrence, start)
    rest = copy.deepcopy(main)
    for name in ("uid", "dtstart", "dtend", "duration", "exdate", "rdate", "sequence"):
        rest.pop(name, None)
    rest.add("uid", str(uuid.uuid4()))
    rest.add("dtstart", at)
    rest.add("dtend", at + duration(main))
    count = _first((main.get("rrule") or {}).get("COUNT"))
    if count:
        rule = dict(rest.get("rrule"))
        rule["COUNT"] = [max(1, int(count) - _dates_before(main, at))]
        rest.pop("rrule", None)
        rest.add("rrule", vRecur(rule))
    series = ICalendar()
    series.add("prodid", "-//Household Hub//CalDAV Connector//EN")
    series.add("version", "2.0")
    series.add_component(rest)
    return series


def shift_series(calendar: ICalendar, start_time: Optional[datetime], end_time: Optional[datetime]) -> None:
    """
    Gives the whole series a new time of day and length. It keeps its first day: [start_time] says
    the hour, on whichever date the model had in mind. The dates skipped and the dates changed on
    their own are named by when the series would have had them, so those names move with it, or a
    strict calendar app would bring the skipped ones back.
    """
    main = master(calendar)
    first = main.get("dtstart").dt
    length = duration(main)
    start = first
    if start_time is not None and isinstance(first, datetime):
        to = like(start_time, first)
        start = first.replace(hour=to.hour, minute=to.minute, second=to.second)
    if end_time is not None:
        asked = like(end_time, first) - (like(start_time, first) if start_time is not None else start)
        length = asked if asked > timedelta(0) else length
    moved = start - first

    for name in ("dtstart", "dtend", "duration"):
        main.pop(name, None)
    main.add("dtstart", start)
    main.add("dtend", start + length)
    if not moved:
        return
    skipped = [entry.dt for group in _groups(main.get("exdate")) for entry in group.dts]
    main.pop("exdate", None)
    for when in skipped:
        main.add("exdate", when + moved)
    for changed in calendar.walk("VEVENT"):
        if "recurrence-id" in changed:
            named = changed.pop("recurrence-id").dt
            changed.add("recurrence-id", named + moved)


def _groups(exdates) -> list:
    """A VEVENT's EXDATE lines: icalendar hands back one, a list of them, or nothing."""
    if not exdates:
        return []
    return exdates if isinstance(exdates, list) else [exdates]


def duration(component: IEvent) -> timedelta:
    start = component.get("dtstart").dt
    end = component.get("dtend")
    if end is not None:
        return end.dt - start
    if component.get("duration") is not None:
        return component.get("duration").dt
    return timedelta(days=1) if not isinstance(start, datetime) else timedelta(0)


def dates(calendar: ICalendar, start, limit: int) -> List[Any]:
    """
    Up to [limit] dates of the series from [start] on, in DTSTART's form, as a strict calendar app
    counts them: the rule's dates, less the ones skipped by an EXDATE that names a date exactly. A
    skip written for a moment the rule never gives skips nothing, which is how Google reads it.
    """
    main = master(calendar)
    first = main.get("dtstart").dt
    skipped = _skipped(main)
    found: List[Any] = []
    if limit < 1:
        return found
    for when in _rule(main).xafter(_in_rule(start, first), inc=True):
        if when in skipped:
            continue
        found.append(when if isinstance(first, datetime) else when.date())
        if len(found) >= limit:
            break
    return found


def occurrence_at(calendar: ICalendar, moment: datetime) -> Optional[Any]:
    """
    The date of the series that [moment] means, in DTSTART's form, or None when it is none of them.
    The same instant is tried first, then the same time of day in the event's own zone: a model
    often writes the local time and calls it UTC. A date already skipped is none of them.
    """
    first = master(calendar).get("dtstart").dt
    exact = like(moment, first)
    if dates(calendar, exact, 1) == [exact]:
        return exact
    if isinstance(first, datetime) and first.tzinfo is not None and moment.tzinfo is not None:
        local = moment.replace(tzinfo=first.tzinfo)
        if dates(calendar, local, 1) == [local]:
            return local
    return None


def nearby(calendar: ICalendar, moment: datetime, count: int = 3) -> List[Any]:
    """The series' date just before [moment] and the ones from it on, [count] at most: what to offer instead."""
    main = master(calendar)
    first = main.get("dtstart").dt
    rule, skipped = _rule(main), _skipped(main)
    before = rule.before(_in_rule(moment, first))
    while before is not None and before in skipped:
        before = rule.before(before)
    around = [] if before is None else [before if isinstance(first, datetime) else before.date()]
    return around + dates(calendar, moment, count - len(around))


def _rule(main: IEvent):
    return rrulestr(main.get("rrule").to_ical().decode(), dtstart=_naive_or_aware(main.get("dtstart").dt))


def _in_rule(value, first) -> datetime:
    """[value], a moment or a day, as the rule of a series starting at [first] counts its dates."""
    moment = value if isinstance(value, datetime) else datetime.combine(value, datetime.min.time())
    return _naive_or_aware(like(moment, first))


def _skipped(main: IEvent) -> set:
    first = main.get("dtstart").dt
    return {_in_rule(entry.dt, first) for group in _groups(main.get("exdate")) for entry in group.dts}


def _dates_before(main: IEvent, at) -> int:
    """How many dates the rule gave before [at]: what a COUNT has already used up."""
    limit = _naive_or_aware(at)
    return sum(1 for when in _rule(main) if when < limit)


def _naive_or_aware(value) -> datetime:
    return value if isinstance(value, datetime) else datetime.combine(value, datetime.min.time())


def _until(until: date, dtstart) -> Any:
    """UNTIL for a rule ending on the day [until], inclusive, in the form RFC 5545 asks for."""
    if not isinstance(dtstart, datetime):
        return until
    end_of_day = datetime.combine(until, datetime.max.time().replace(microsecond=0))
    if dtstart.tzinfo is None:
        return end_of_day
    return end_of_day.replace(tzinfo=dtstart.tzinfo).astimezone(timezone.utc)


def _until_before(occurrence: datetime, dtstart) -> Any:
    at = like(occurrence, dtstart)
    if not isinstance(dtstart, datetime):
        return at - timedelta(days=1)
    before = at - timedelta(seconds=1)
    return before if before.tzinfo is None else before.astimezone(timezone.utc)


def _drop(calendar: ICalendar, doomed) -> None:
    calendar.subcomponents = [c for c in calendar.subcomponents if not (c.name == "VEVENT" and doomed(c))]


def _first(value):
    if isinstance(value, list):
        return value[0] if value else None
    return value
