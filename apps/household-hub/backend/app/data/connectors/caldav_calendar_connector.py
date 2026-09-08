import asyncio
from datetime import datetime, timezone
from typing import List, Optional
import caldav
from icalendar import Calendar as ICalendar, Event as IEvent
import uuid

from app.domain.entities.calendar_event import CalendarEvent
from app.domain.entities.integration_credential import CalendarCredential
from app.domain.exceptions import CalendarAuthException, CalendarIntegrationException
from app.domain.repositories.calendar_connector import ICalendarConnector


class CalDavCalendarConnector(ICalendarConnector):
    """
    CalDAV connector supporting Apple iCloud, Google Calendar, and self-hosted
    CalDAV servers using standard RFC 4791 / 5545 iCalendar format.
    All synchronous socket calls are dispatched to asyncio.to_thread.
    """

    def _sync_get_client(self, credential: CalendarCredential, secret: str) -> caldav.DAVClient:
        return caldav.DAVClient(
            url=credential.url,
            username=credential.username,
            password=secret,
        )

    def _sync_test_connection(self, credential: CalendarCredential, secret: str) -> bool:
        client = self._sync_get_client(credential, secret)
        try:
            principal = client.principal()
            calendars = principal.calendars()
            return len(calendars) >= 0
        except Exception:
            return False

    def _sync_get_target_calendar(self, client: caldav.DAVClient, calendar_name: str):
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
            target_cal = self._sync_get_target_calendar(client, credential.calendar_name)
            raw_events = target_cal.date_search(start=start_time, end=end_time, expand=True)

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
                            )
                        )
                except Exception:
                    continue

            return sorted(events, key=lambda e: e.start_time)

        except Exception as e:
            if isinstance(e, CalendarIntegrationException):
                raise
            raise CalendarIntegrationException(f"Failed to fetch CalDAV events: {str(e)}")

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
    ) -> CalendarEvent:
        client = self._sync_get_client(credential, secret)
        try:
            target_cal = self._sync_get_target_calendar(client, credential.calendar_name)
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

            cal.add_component(event)
            target_cal.add_event(cal.to_ical())

            return CalendarEvent(
                id=uid,
                title=title,
                start_time=start_time,
                end_time=end_time,
                description=description,
                location=location,
                is_all_day=is_all_day,
                calendar_name=target_cal.name or credential.calendar_name,
            )
        except Exception as e:
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
    ) -> CalendarEvent:
        client = self._sync_get_client(credential, secret)
        try:
            target_cal = self._sync_get_target_calendar(client, credential.calendar_name)
            try:
                event = target_cal.event_by_uid(event_id)
            except Exception as e:
                raise CalendarIntegrationException(f"Event '{event_id}' not found on CalDAV calendar: {str(e)}")

            if not event:
                raise CalendarIntegrationException(f"Event '{event_id}' not found on CalDAV calendar.")

            cal_obj = ICalendar.from_ical(event.data)
            updated_title = title
            updated_desc = description
            updated_loc = location
            updated_start = start_time
            updated_end = end_time
            updated_is_all_day = is_all_day

            for component in cal_obj.walk("VEVENT"):
                if title is not None:
                    component["summary"] = title
                elif "summary" in component and not updated_title:
                    updated_title = str(component["summary"])

                if description is not None:
                    component["description"] = description
                elif "description" in component and not updated_desc:
                    updated_desc = str(component["description"])

                if location is not None:
                    component["location"] = location
                elif "location" in component and not updated_loc:
                    updated_loc = str(component["location"])

                if start_time is not None:
                    component["dtstart"] = start_time
                elif "dtstart" in component and not updated_start:
                    dtstart_val = component.get("dtstart").dt
                    if not isinstance(dtstart_val, datetime):
                        updated_start = datetime.combine(dtstart_val, datetime.min.time(), tzinfo=timezone.utc)
                    elif dtstart_val.tzinfo is None:
                        updated_start = dtstart_val.replace(tzinfo=timezone.utc)
                    else:
                        updated_start = dtstart_val

                if end_time is not None:
                    component["dtend"] = end_time
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
                component["sequence"] = seq + 1
                component["dtstamp"] = datetime.now(timezone.utc)

            raw_ical = cal_obj.to_ical()
            event.data = raw_ical.decode("utf-8") if isinstance(raw_ical, (bytes, bytearray)) else str(raw_ical)
            event.save()

            return CalendarEvent(
                id=event_id,
                title=updated_title or "Updated Event",
                start_time=updated_start or datetime.now(timezone.utc),
                end_time=updated_end or datetime.now(timezone.utc),
                description=updated_desc or "",
                location=updated_loc or "",
                is_all_day=bool(updated_is_all_day),
                calendar_name=target_cal.name or credential.calendar_name,
            )
        except Exception as e:
            if isinstance(e, CalendarIntegrationException):
                raise
            raise CalendarIntegrationException(f"Failed to update CalDAV event '{event_id}': {str(e)}")

    def _sync_delete_event(
        self,
        credential: CalendarCredential,
        secret: str,
        event_id: str,
    ) -> bool:
        client = self._sync_get_client(credential, secret)
        try:
            target_cal = self._sync_get_target_calendar(client, credential.calendar_name)
            event = target_cal.event_by_uid(event_id)
            event.delete()
            return True
        except Exception as e:
            raise CalendarIntegrationException(f"Failed to delete CalDAV event '{event_id}': {str(e)}")

    async def test_connection(self, credential: CalendarCredential, secret: str) -> bool:
        return await asyncio.to_thread(self._sync_test_connection, credential, secret)

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
    ) -> bool:
        try:
            return await asyncio.wait_for(
                asyncio.to_thread(
                    self._sync_delete_event,
                    credential,
                    secret,
                    event_id,
                ),
                timeout=timeout,
            )
        except asyncio.TimeoutError:
            raise CalendarIntegrationException(f"CalDAV event deletion timed out after {timeout:.1f}s.")
