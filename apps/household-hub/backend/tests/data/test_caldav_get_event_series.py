"""
Reading one event by its ID when it repeats (#63).

A repeating event is one calendar object: the series, plus an entry per date changed on its own, all
under one ID. A remove or change card is about one of its dates, so the read names that date.
"""
from datetime import datetime, timezone
from unittest.mock import MagicMock, patch

import pytest

from app.data.connectors.caldav_calendar_connector import CalDavCalendarConnector
from app.domain.entities.calendar_event import Repeat
from app.domain.entities.integration_credential import CalendarCredential

CREDENTIAL = CalendarCredential(
    id="cred-1",
    user_id="user-1",
    provider="apple_icloud",
    url="https://caldav.icloud.com",
    username="emma@icloud.com",
    encrypted_secret="enc",
    calendar_name="Default",
)

# The date changed on its own comes first in the file, as a server may well store it.
MOVED_DATE = (
    "BEGIN:VEVENT\r\nUID:series-1\r\nDTSTAMP:20261001T000000Z\r\n"
    "RECURRENCE-ID:20261013T080000Z\r\n"
    "DTSTART:20261013T140000Z\r\nDTEND:20261013T150000Z\r\nSUMMARY:Series test (moved)\r\n"
    "END:VEVENT\r\n"
)
SERIES = (
    "BEGIN:VEVENT\r\nUID:series-1\r\nDTSTAMP:20261001T000000Z\r\n"
    "DTSTART:20261006T080000Z\r\nDTEND:20261006T083000Z\r\nSUMMARY:Series test\r\n"
    "RRULE:FREQ=WEEKLY;COUNT=4\r\n"
    "END:VEVENT\r\n"
)
SERIES_ICAL = (
    "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Example//CalDAV//EN\r\n" + MOVED_DATE + SERIES + "END:VCALENDAR\r\n"
)


async def _read(occurrence_start=None):
    connector = CalDavCalendarConnector()
    item = MagicMock()
    item.data = SERIES_ICAL
    calendar = MagicMock()
    calendar.name = "Default"
    calendar.event_by_uid.return_value = item
    with patch.object(connector, "_sync_get_client"), patch.object(connector, "_sync_get_target_calendar", return_value=calendar):
        return await connector.get_event(CREDENTIAL, "secret", "series-1", occurrence_start=occurrence_start)


@pytest.mark.asyncio
async def test_GIVEN_a_repeating_event_WHEN_read_by_its_id_THEN_it_is_the_series_and_how_it_repeats():
    event = await _read()

    assert event.title == "Series test"
    assert event.start_time == datetime(2026, 10, 6, 8, 0, tzinfo=timezone.utc)
    assert event.end_time == datetime(2026, 10, 6, 8, 30, tzinfo=timezone.utc)
    assert event.repeat == Repeat(frequency="weekly", count=4)
    assert event.occurrence_start is None


@pytest.mark.asyncio
async def test_GIVEN_one_date_of_a_repeating_event_WHEN_read_THEN_it_is_that_date_with_the_series_length():
    third = datetime(2026, 10, 20, 8, 0, tzinfo=timezone.utc)

    event = await _read(occurrence_start=third)

    assert event.title == "Series test"
    assert event.start_time == third
    assert event.end_time == datetime(2026, 10, 20, 8, 30, tzinfo=timezone.utc)
    assert event.occurrence_start == third
    assert event.repeat == Repeat(frequency="weekly", count=4)


@pytest.mark.asyncio
async def test_GIVEN_a_date_changed_on_its_own_WHEN_read_THEN_it_is_as_the_member_sees_it():
    second = datetime(2026, 10, 13, 8, 0, tzinfo=timezone.utc)

    event = await _read(occurrence_start=second)

    assert event.title == "Series test (moved)"
    assert event.start_time == datetime(2026, 10, 13, 14, 0, tzinfo=timezone.utc)
    assert event.end_time == datetime(2026, 10, 13, 15, 0, tzinfo=timezone.utc)
    assert event.occurrence_start == second


@pytest.mark.asyncio
async def test_GIVEN_a_date_the_series_does_not_have_WHEN_read_THEN_it_is_the_series_with_no_date_named():
    event = await _read(occurrence_start=datetime(2026, 10, 21, 8, 0, tzinfo=timezone.utc))

    assert event.title == "Series test"
    assert event.repeat == Repeat(frequency="weekly", count=4)
    assert event.occurrence_start is None
