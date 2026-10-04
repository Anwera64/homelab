import asyncio
from datetime import datetime, timezone
from typing import Dict, List, Optional
import caldav
from caldav.lib.error import AuthorizationError, NotFoundError
from icalendar import Calendar as ICalendar, Event as IEvent
import uuid

from app.data.connectors import ical_series
from app.domain.entities.calendar_event import THIS_AND_FOLLOWING, CalendarEvent, Repeat
from app.domain.entities.integration_credential import OAUTH, CalendarCredential
from app.domain.exceptions import (
    CalendarAuthException,
    CalendarIntegrationException,
    CalendarUnreachableException,
    CalendarWriteNotConfirmedException,
)
from app.domain.repositories.calendar_connector import ICalendarConnector


def _replace(component, name: str, value) -> None:
    """
    Sets a property through icalendar's `add`, which writes it in iCalendar form. Assigning a raw
    datetime writes Python's str() of it, which Google refuses with a 400.
    """
    component.pop(name, None)
    component.add(name, value)


def _save(event, cal_obj: ICalendar) -> None:
    raw_ical = cal_obj.to_ical()
    event.data = raw_ical.decode("utf-8") if isinstance(raw_ical, (bytes, bytearray)) else str(raw_ical)
    event.save()


def _bump(component) -> None:
    """Marks [component] as changed, so other calendar apps pick the new version up."""
    _replace(component, "sequence", int(component.get("sequence", 0)) + 1)
    _replace(component, "dtstamp", datetime.now(timezone.utc))


def _change(component, title, start_time, end_time, description, location) -> None:
    """
    Applies a change to one VEVENT of a series. Times keep the form the event has: a whole day stays
    a date, and an event in a zone stays in it.
    """
    if title is not None:
        _replace(component, "summary", title)
    if description is not None:
        _replace(component, "description", description)
    if location is not None:
        _replace(component, "location", location)
    if start_time is not None or end_time is not None:
        current = component.get("dtstart").dt
        length = ical_series.duration(component)
        start = ical_series.like(start_time, current) if start_time is not None else current
        end = ical_series.like(end_time, current) if end_time is not None else start + length
        _replace(component, "dtstart", start)
        component.pop("duration", None)
        _replace(component, "dtend", end)
    _bump(component)


def _real_occurrence(cal_obj: ICalendar, occurrence_start: Optional[datetime], event_id: str) -> datetime:
    """
    The date of the series a write is for, as the series itself has it. What the model named is only
    a pointer to it: a date the series doesn't have is refused, with the ones it does have, rather
    than written and reported as done.
    """
    if occurrence_start is None:
        raise CalendarIntegrationException(
            f"Event '{event_id}' repeats. Say which of its dates you mean with occurrence_start, as "
            "calendar_read gave it, and with scope whether it is for that date only or it and the ones after."
        )
    found = ical_series.occurrence_at(cal_obj, occurrence_start)
    if found is None:
        real = ", ".join(when.isoformat() for when in ical_series.nearby(cal_obj, occurrence_start))
        raise CalendarIntegrationException(
            f"Event '{event_id}' has no date at {occurrence_start.isoformat()}, so nothing was changed. "
            f"Its dates around then: {real or 'none'}. Use one of those as occurrence_start."
        )
    return ical_series.as_moment(found)


def _read_back(target_cal, uid: str) -> Optional[ICalendar]:
    """The event as the calendar holds it now, or None when it has none by that id."""
    try:
        return ICalendar.from_ical(target_cal.event_by_uid(uid).data)
    except NotFoundError:
        return None
    except Exception as e:
        raise CalendarWriteNotConfirmedException(
            f"The calendar took the write but could not be read back to confirm it: {e}"
        )


def _confirm_written(target_cal, written: ICalendar, around: Optional[datetime] = None) -> None:
    """
    Reads an event back after writing it and checks the calendar holds what was written: each of its
    entries by title and time, and, for a repeating event, the same dates from [around] on. A server
    answering "saved" is not taken as proof, and neither is the model saying so afterwards. Times
    are compared as moments, since servers rename zones and keep the moment.
    """
    main = ical_series.master(written)
    name = str(main.get("summary", main.get("uid")))
    stored = _read_back(target_cal, str(main.get("uid")))
    if stored is None:
        raise CalendarWriteNotConfirmedException(f"The calendar does not have '{name}' after writing it.")

    for wrote in written.walk("VEVENT"):
        kept = _entry_like(stored, wrote)
        if kept is None:
            raise CalendarWriteNotConfirmedException(f"The calendar did not keep the change to '{name}'.")
        if _shown(kept) != _shown(wrote):
            title, start, _ = _shown(kept)
            raise CalendarWriteNotConfirmedException(
                f"The calendar did not keep the change: it has '{title}' at {start.isoformat()}."
            )

    if not ical_series.repeats(written) and not ical_series.repeats(stored):
        return
    start = around or ical_series.as_moment(main.get("dtstart").dt)
    has = _dates(stored, start) if ical_series.repeats(stored) else None
    if not ical_series.repeats(written) or has != _dates(written, start):
        shows = "it does not repeat" if has is None else ", ".join(when.isoformat() for when in has) or "none"
        raise CalendarWriteNotConfirmedException(
            f"The calendar did not keep the change to '{name}'. Its dates from {start.isoformat()} are: {shows}."
        )


def _confirm_gone(target_cal, uid: str) -> None:
    if _read_back(target_cal, uid) is not None:
        raise CalendarWriteNotConfirmedException(f"The calendar still has event '{uid}' after removing it.")


def _entry_like(stored: ICalendar, wrote: IEvent) -> Optional[IEvent]:
    """The entry of [stored] for the same date of the series as [wrote]: the series itself, or one changed date."""
    if "recurrence-id" not in wrote:
        return ical_series.master(stored)
    occurrence = ical_series.as_moment(wrote["recurrence-id"].dt)
    return next((c for c in stored.walk("VEVENT") if ical_series.same_occurrence(c, occurrence)), None)


def _shown(component: IEvent):
    """What a member sees of an entry: its title, and when it starts and ends."""
    start = ical_series.as_moment(component.get("dtstart").dt)
    return str(component.get("summary", "")), start, start + ical_series.duration(component)


def _dates(calendar: ICalendar, start: datetime) -> List[datetime]:
    return [ical_series.as_moment(when) for when in ical_series.dates(calendar, start, CONFIRMED_DATES)]


# How many of a series' dates are compared after a write: enough to show a skip, a move or an end.
CONFIRMED_DATES = 5


class CalDavCalendarConnector(ICalendarConnector):
    """
    CalDAV connector supporting Apple iCloud, Google Calendar, and self-hosted
    CalDAV servers using standard RFC 4791 / 5545 iCalendar format.
    All synchronous socket calls are dispatched to asyncio.to_thread.
    """

    def _sync_get_client(self, credential: CalendarCredential, secret: str) -> caldav.DAVClient:
        if credential.auth_kind == OAUTH:
            return caldav.DAVClient(url=credential.url, password=secret, auth_type="bearer")
        return caldav.DAVClient(
            url=credential.url,
            username=credential.username,
            password=secret,
        )

    def _sync_test_connection(self, credential: CalendarCredential, secret: str) -> bool:
        """
        True when the account answers. A refused password and a server that can't be reached are
        different fixes for the member, so they raise different exceptions rather than one False.
        """
        try:
            client = self._sync_get_client(credential, secret)
            if credential.auth_kind == OAUTH:
                client.calendar(url=credential.url).get_display_name()
            else:
                client.principal().calendars()
            return True
        except AuthorizationError as e:
            raise CalendarAuthException(f"CalDAV server at {credential.url} refused the credentials: {e}")
        except Exception as e:
            raise CalendarUnreachableException(f"CalDAV server at {credential.url} could not be reached: {e}")

    def _sync_get_target_calendar(self, client: caldav.DAVClient, credential: CalendarCredential):
        # A Google sign-in's address is the calendar itself; Google's CalDAV has no discovery to lean on.
        if credential.auth_kind == OAUTH:
            return client.calendar(url=credential.url)

        calendar_name = credential.calendar_name
        principal = client.principal()
        calendars = principal.calendars()
        if not calendars:
            raise CalendarIntegrationException("No calendars found on the CalDAV account.")

        if calendar_name and calendar_name != "Default":
            for cal in calendars:
                if cal.name and cal.name.lower() == calendar_name.lower():
                    return cal

        # Default to the first calendar
        return calendars[0]

    def _sync_fetch_events(
        self,
        credential: CalendarCredential,
        secret: str,
        start_time: datetime,
        end_time: datetime,
        limit: int,
    ) -> List[CalendarEvent]:
        client = self._sync_get_client(credential, secret)
        try:
            target_cal = self._sync_get_target_calendar(client, credential)
            raw_events = target_cal.date_search(start=start_time, end=end_time, expand=True)
            rules = self._sync_series_rules(target_cal, start_time, end_time)

            events: List[CalendarEvent] = []
            for item in raw_events[:limit]:
                try:
                    cal_obj = ICalendar.from_ical(item.data)
                    for component in cal_obj.walk("VEVENT"):
                        uid = str(component.get("uid", item.url or str(uuid.uuid4())))
                        summary = str(component.get("summary", "Untitled Event"))
                        description = str(component.get("description", ""))
                        location = str(component.get("location", ""))

                        dtstart = component.get("dtstart")
                        dtend = component.get("dtend")

                        evt_start = dtstart.dt if dtstart else start_time
                        evt_end = dtend.dt if dtend else evt_start

                        is_all_day = not isinstance(evt_start, datetime)
                        repeat = rules.get(uid)
                        of_series = repeat is not None or "recurrence-id" in component

                        # Normalize to datetime with timezone
                        if not isinstance(evt_start, datetime):
                            evt_start = datetime.combine(evt_start, datetime.min.time(), tzinfo=timezone.utc)
                        elif evt_start.tzinfo is None:
                            evt_start = evt_start.replace(tzinfo=timezone.utc)

                        if not isinstance(evt_end, datetime):
                            evt_end = datetime.combine(evt_end, datetime.min.time(), tzinfo=timezone.utc)
                        elif evt_end.tzinfo is None:
                            evt_end = evt_end.replace(tzinfo=timezone.utc)

                        events.append(
                            CalendarEvent(
                                id=uid,
                                title=summary,
                                start_time=evt_start,
                                end_time=evt_end,
                                description=description,
                                location=location,
                                is_all_day=is_all_day,
                                calendar_name=target_cal.name or credential.calendar_name,
                                repeat=repeat,
                                occurrence_start=ical_series.occurrence_of(component) if of_series else None,
                            )
                        )
                except Exception:
                    continue

            return sorted(events, key=lambda e: e.start_time)

        except Exception as e:
            if isinstance(e, CalendarIntegrationException):
                raise
            _raise_if_refused(e, credential)
            raise CalendarIntegrationException(f"Failed to fetch CalDAV events: {str(e)}")

    def _sync_series_rules(self, target_cal, start_time: datetime, end_time: datetime) -> Dict[str, Repeat]:
        """
        How each repeating event in the window repeats, by UID. An expanded search hands back each
        date without its rule, so the series themselves are asked for once more, unexpanded. Without
        them the dates still read, just without saying how they repeat.
        """
        try:
            series = target_cal.date_search(start=start_time, end=end_time, expand=False)
        except Exception:
            return {}
        rules: Dict[str, Repeat] = {}
        for item in series:
            try:
                main = ical_series.master(ICalendar.from_ical(item.data))
                repeat = ical_series.repeat_of(main) if main is not None else None
                if repeat is not None:
                    rules[str(main.get("uid"))] = repeat
            except Exception:
                continue
        return rules

    def _sync_create_event(
        self,
        credential: CalendarCredential,
        secret: str,
        title: str,
        start_time: datetime,
        end_time: datetime,
        description: str,
        location: str,
        is_all_day: bool,
        repeat: Optional[Repeat] = None,
    ) -> CalendarEvent:
        client = self._sync_get_client(credential, secret)
        try:
            target_cal = self._sync_get_target_calendar(client, credential)
            cal = ICalendar()
            cal.add("prodid", "-//Household Hub//CalDAV Connector//EN")
            cal.add("version", "2.0")

            event = IEvent()
            uid = str(uuid.uuid4())
            event.add("uid", uid)
            event.add("summary", title)
            event.add("dtstart", start_time)
            event.add("dtend", end_time)
            if description:
                event.add("description", description)
            if location:
                event.add("location", location)
            event.add("dtstamp", datetime.now(timezone.utc))
            if repeat is not None:
                ical_series.set_repeat(event, repeat)

            cal.add_component(event)
            target_cal.add_event(cal.to_ical())
            _confirm_written(target_cal, cal)

            return CalendarEvent(
                id=uid,
                title=title,
                start_time=start_time,
                end_time=end_time,
                description=description,
                location=location,
                is_all_day=is_all_day,
                calendar_name=target_cal.name or credential.calendar_name,
                repeat=repeat,
            )
        except CalendarIntegrationException:
            raise
        except Exception as e:
            _raise_if_refused(e, credential)
            raise CalendarIntegrationException(f"Failed to create CalDAV event: {str(e)}")

    def _sync_update_event(
        self,
        credential: CalendarCredential,
        secret: str,
        event_id: str,
        title: Optional[str] = None,
        start_time: Optional[datetime] = None,
        end_time: Optional[datetime] = None,
        description: Optional[str] = None,
        location: Optional[str] = None,
        is_all_day: Optional[bool] = None,
        repeat: Optional[Repeat] = None,
        occurrence_start: Optional[datetime] = None,
        scope: Optional[str] = None,
    ) -> CalendarEvent:
        client = self._sync_get_client(credential, secret)
        try:
            target_cal = self._sync_get_target_calendar(client, credential)
            try:
                event = target_cal.event_by_uid(event_id)
            except Exception as e:
                _raise_if_refused(e, credential)
                raise CalendarIntegrationException(f"Event '{event_id}' not found on CalDAV calendar: {str(e)}")

            if not event:
                raise CalendarIntegrationException(f"Event '{event_id}' not found on CalDAV calendar.")

            cal_obj = ICalendar.from_ical(event.data)
            if ical_series.repeats(cal_obj):
                return self._sync_update_series(
                    target_cal, credential, event, cal_obj, event_id, occurrence_start, scope,
                    title, start_time, end_time, description, location, repeat,
                )

            updated_title = title
            updated_desc = description
            updated_loc = location
            updated_start = start_time
            updated_end = end_time
            updated_is_all_day = is_all_day

            for component in cal_obj.walk("VEVENT"):
                if title is not None:
                    _replace(component, "summary", title)
                elif "summary" in component and not updated_title:
                    updated_title = str(component["summary"])

                if description is not None:
                    _replace(component, "description", description)
                elif "description" in component and not updated_desc:
                    updated_desc = str(component["description"])

                if location is not None:
                    _replace(component, "location", location)
                elif "location" in component and not updated_loc:
                    updated_loc = str(component["location"])

                if start_time is not None:
                    _replace(component, "dtstart", start_time)
                elif "dtstart" in component and not updated_start:
                    dtstart_val = component.get("dtstart").dt
                    if not isinstance(dtstart_val, datetime):
                        updated_start = datetime.combine(dtstart_val, datetime.min.time(), tzinfo=timezone.utc)
                    elif dtstart_val.tzinfo is None:
                        updated_start = dtstart_val.replace(tzinfo=timezone.utc)
                    else:
                        updated_start = dtstart_val

                if end_time is not None:
                    _replace(component, "dtend", end_time)
                elif "dtend" in component and not updated_end:
                    dtend_val = component.get("dtend").dt
                    if not isinstance(dtend_val, datetime):
                        updated_end = datetime.combine(dtend_val, datetime.min.time(), tzinfo=timezone.utc)
                    elif dtend_val.tzinfo is None:
                        updated_end = dtend_val.replace(tzinfo=timezone.utc)
                    else:
                        updated_end = dtend_val

                if is_all_day is not None:
                    updated_is_all_day = is_all_day
                elif "dtstart" in component and updated_is_all_day is None:
                    updated_is_all_day = not isinstance(component.get("dtstart").dt, datetime)

                # Bump sequence number and update DTSTAMP
                seq = int(component.get("sequence", 0))
                _replace(component, "sequence", seq + 1)
                _replace(component, "dtstamp", datetime.now(timezone.utc))

            if repeat is not None:
                ical_series.set_repeat(ical_series.master(cal_obj), repeat)

            _save(event, cal_obj)
            _confirm_written(target_cal, cal_obj)

            return CalendarEvent(
                id=event_id,
                title=updated_title or "Updated Event",
                start_time=updated_start or datetime.now(timezone.utc),
                end_time=updated_end or datetime.now(timezone.utc),
                description=updated_desc or "",
                location=updated_loc or "",
                is_all_day=bool(updated_is_all_day),
                calendar_name=target_cal.name or credential.calendar_name,
                repeat=repeat,
            )
        except Exception as e:
            if isinstance(e, CalendarIntegrationException):
                raise
            _raise_if_refused(e, credential)
            raise CalendarIntegrationException(f"Failed to update CalDAV event '{event_id}': {str(e)}")

    def _sync_update_series(
        self,
        target_cal,
        credential: CalendarCredential,
        event,
        cal_obj: ICalendar,
        event_id: str,
        occurrence_start: Optional[datetime],
        scope: Optional[str],
        title: Optional[str],
        start_time: Optional[datetime],
        end_time: Optional[datetime],
        description: Optional[str],
        location: Optional[str],
        repeat: Optional[Repeat],
    ) -> CalendarEvent:
        """
        Changes one date of a repeating event, or that date and every later one. Changing the whole
        series from its first date is the "following" case at that date. A later date splits the
        series: the old one ends the day before, a new one carries on changed.
        """
        occurrence = _real_occurrence(cal_obj, occurrence_start, event_id)
        main = ical_series.master(cal_obj)
        rest = None
        if scope != THIS_AND_FOLLOWING:
            changed = ical_series.override(cal_obj, occurrence)
        elif ical_series.is_first(cal_obj, occurrence):
            changed = main
        else:
            rest = ical_series.following(cal_obj, occurrence)
            ical_series.end_before(cal_obj, occurrence)
            changed = ical_series.master(rest)
        _change(changed, title, start_time, end_time, description, location)
        if repeat is not None and scope == THIS_AND_FOLLOWING:
            ical_series.set_repeat(changed, repeat)

        _save(event, cal_obj)
        if rest is not None:
            target_cal.add_event(rest.to_ical())
        _confirm_written(target_cal, cal_obj, around=occurrence)
        if rest is not None:
            _confirm_written(target_cal, rest)

        start = ical_series.as_moment(changed.get("dtstart").dt)
        return CalendarEvent(
            id=str(changed.get("uid")),
            title=str(changed.get("summary", "")),
            start_time=start,
            end_time=start + ical_series.duration(changed),
            description=str(changed.get("description", "")),
            location=str(changed.get("location", "")),
            is_all_day=not isinstance(changed.get("dtstart").dt, datetime),
            calendar_name=target_cal.name or credential.calendar_name,
            repeat=ical_series.repeat_of(changed) if scope == THIS_AND_FOLLOWING else None,
            occurrence_start=occurrence,
        )

    def _sync_delete_event(
        self,
        credential: CalendarCredential,
        secret: str,
        event_id: str,
        occurrence_start: Optional[datetime] = None,
        scope: Optional[str] = None,
    ) -> bool:
        client = self._sync_get_client(credential, secret)
        try:
            target_cal = self._sync_get_target_calendar(client, credential)
            try:
                event = target_cal.event_by_uid(event_id)
            except Exception as e:
                _raise_if_refused(e, credential)
                raise CalendarIntegrationException(f"Event '{event_id}' not found on CalDAV calendar: {str(e)}")
            cal_obj = ICalendar.from_ical(event.data)
            if not ical_series.repeats(cal_obj):
                event.delete()
                _confirm_gone(target_cal, event_id)
                return True

            occurrence = _real_occurrence(cal_obj, occurrence_start, event_id)
            if scope != THIS_AND_FOLLOWING:
                ical_series.skip(cal_obj, occurrence)
            elif not ical_series.end_before(cal_obj, occurrence):
                event.delete()
                _confirm_gone(target_cal, event_id)
                return True
            _bump(ical_series.master(cal_obj))
            _save(event, cal_obj)
            _confirm_written(target_cal, cal_obj, around=occurrence)
            return True
        except CalendarIntegrationException:
            raise
        except Exception as e:
            _raise_if_refused(e, credential)
            raise CalendarIntegrationException(f"Failed to delete CalDAV event '{event_id}': {str(e)}")

    async def test_connection(self, credential: CalendarCredential, secret: str, timeout: float = 15.0) -> bool:
        try:
            return await asyncio.wait_for(
                asyncio.to_thread(self._sync_test_connection, credential, secret),
                timeout=timeout,
            )
        except asyncio.TimeoutError:
            raise CalendarUnreachableException(f"CalDAV server at {credential.url} did not answer within {timeout:.0f}s.")

    async def fetch_events(
        self,
        credential: CalendarCredential,
        secret: str,
        start_time: datetime,
        end_time: datetime,
        limit: int = 50,
        timeout: float = 10.0,
    ) -> List[CalendarEvent]:
        try:
            return await asyncio.wait_for(
                asyncio.to_thread(
                    self._sync_fetch_events,
                    credential,
                    secret,
                    start_time,
                    end_time,
                    limit,
                ),
                timeout=timeout,
            )
        except asyncio.TimeoutError:
            raise CalendarIntegrationException(f"CalDAV calendar fetch timed out after {timeout:.1f}s.")

    async def create_event(
        self,
        credential: CalendarCredential,
        secret: str,
        title: str,
        start_time: datetime,
        end_time: datetime,
        description: str = "",
        location: str = "",
        is_all_day: bool = False,
        timeout: float = 10.0,
        repeat: Optional[Repeat] = None,
    ) -> CalendarEvent:
        try:
            return await asyncio.wait_for(
                asyncio.to_thread(
                    self._sync_create_event,
                    credential,
                    secret,
                    title,
                    start_time,
                    end_time,
                    description,
                    location,
                    is_all_day,
                    repeat,
                ),
                timeout=timeout,
            )
        except asyncio.TimeoutError:
            raise CalendarIntegrationException(f"CalDAV event creation timed out after {timeout:.1f}s.")

    async def update_event(
        self,
        credential: CalendarCredential,
        secret: str,
        event_id: str,
        title: Optional[str] = None,
        start_time: Optional[datetime] = None,
        end_time: Optional[datetime] = None,
        description: Optional[str] = None,
        location: Optional[str] = None,
        is_all_day: Optional[bool] = None,
        timeout: float = 10.0,
        repeat: Optional[Repeat] = None,
        occurrence_start: Optional[datetime] = None,
        scope: Optional[str] = None,
    ) -> CalendarEvent:
        try:
            return await asyncio.wait_for(
                asyncio.to_thread(
                    self._sync_update_event,
                    credential,
                    secret,
                    event_id,
                    title,
                    start_time,
                    end_time,
                    description,
                    location,
                    is_all_day,
                    repeat,
                    occurrence_start,
                    scope,
                ),
                timeout=timeout,
            )
        except asyncio.TimeoutError:
            raise CalendarIntegrationException(f"CalDAV event update timed out after {timeout:.1f}s.")

    async def delete_event(
        self,
        credential: CalendarCredential,
        secret: str,
        event_id: str,
        timeout: float = 10.0,
        occurrence_start: Optional[datetime] = None,
        scope: Optional[str] = None,
    ) -> bool:
        try:
            return await asyncio.wait_for(
                asyncio.to_thread(
                    self._sync_delete_event,
                    credential,
                    secret,
                    event_id,
                    occurrence_start,
                    scope,
                ),
                timeout=timeout,
            )
        except asyncio.TimeoutError:
            raise CalendarIntegrationException(f"CalDAV event deletion timed out after {timeout:.1f}s.")


def _raise_if_refused(error: Exception, credential: CalendarCredential) -> None:
    """
    A password revoked after connecting shows up on the next read or write. It says "sign in again",
    not "something failed", so the failed step can offer the member the fix.
    """
    if isinstance(error, AuthorizationError):
        raise CalendarAuthException(f"CalDAV server at {credential.url} refused the credentials: {error}")
