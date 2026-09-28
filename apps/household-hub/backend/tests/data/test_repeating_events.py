"""Repeating calendar events over CalDAV: making them, and changing or removing one date or the rest."""

import re
from datetime import date, datetime, timezone
from unittest.mock import MagicMock, patch
from zoneinfo import ZoneInfo

import pytest
from icalendar import Calendar as ICalendar

from app.data.connectors.caldav_calendar_connector import CalDavCalendarConnector
from app.domain.entities.calendar_event import Repeat
from app.domain.entities.integration_credential import CalendarCredential
from app.domain.exceptions import CalendarIntegrationException
from app.domain.use_cases.integrations import calendar_arguments
from app.domain.use_cases.integrations.calendar_arguments import CalendarArgumentError

# Gym on Tuesdays and Thursdays at 07:00 UTC from Tue 29 Sep 2026.
GYM = (
    "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Example//CalDAV//EN\r\n"
    "BEGIN:VEVENT\r\nUID:gym-1\r\nDTSTAMP:20260901T000000Z\r\n"
    "DTSTART:20260929T070000Z\r\nDTEND:20260929T080000Z\r\nSUMMARY:Gym\r\n"
    "RRULE:FREQ=WEEKLY;BYDAY=TU,TH;UNTIL=20261224T235959Z\r\n"
    "END:VEVENT\r\nEND:VCALENDAR\r\n"
)

THU_1_OCT = datetime(2026, 10, 1, 7, 0, tzinfo=timezone.utc)
TUE_6_OCT = datetime(2026, 10, 6, 7, 0, tzinfo=timezone.utc)
FIRST = datetime(2026, 9, 29, 7, 0, tzinfo=timezone.utc)


def _credential():
    return CalendarCredential(
        id="cred-1",
        user_id="user-1",
        provider="apple_icloud",
        url="https://caldav.icloud.com",
        username="emma@icloud.com",
        encrypted_secret="enc",
        calendar_name="Home",
    )


def _stored(data):
    event = MagicMock()
    event.data = data
    calendar = MagicMock()
    calendar.name = "Home"
    calendar.event_by_uid.return_value = event
    return event, calendar


def _events(data):
    return list(ICalendar.from_ical(data).walk("VEVENT"))


async def _update(calendar, **changes):
    connector = CalDavCalendarConnector()
    with patch.object(connector, "_sync_get_client"), patch.object(connector, "_sync_get_target_calendar", return_value=calendar):
        return await connector.update_event(credential=_credential(), secret="s", event_id="gym-1", **changes)


async def _delete(calendar, **which):
    connector = CalDavCalendarConnector()
    with patch.object(connector, "_sync_get_client"), patch.object(connector, "_sync_get_target_calendar", return_value=calendar):
        return await connector.delete_event(credential=_credential(), secret="s", event_id="gym-1", **which)


@pytest.mark.asyncio
async def test_a_new_weekly_event_is_written_with_its_repeat_rule():
    connector = CalDavCalendarConnector()
    calendar = MagicMock()
    calendar.name = "Home"
    with patch.object(connector, "_sync_get_client"), patch.object(connector, "_sync_get_target_calendar", return_value=calendar):
        created = await connector.create_event(
            credential=_credential(),
            secret="s",
            title="Gym",
            start_time=FIRST,
            end_time=datetime(2026, 9, 29, 8, 0, tzinfo=timezone.utc),
            repeat=Repeat(frequency="weekly", days=["TU", "TH"], until=date(2026, 12, 24)),
        )

    written = calendar.add_event.call_args[0][0].decode()
    assert re.search(r"^RRULE:FREQ=WEEKLY;UNTIL=20261224T235959Z;BYDAY=TU,TH\r?$", written, re.MULTILINE), written
    assert created.repeat.days == ["TU", "TH"]


@pytest.mark.asyncio
async def test_a_new_all_day_event_repeating_a_number_of_times_counts_them():
    connector = CalDavCalendarConnector()
    calendar = MagicMock()
    with patch.object(connector, "_sync_get_client"), patch.object(connector, "_sync_get_target_calendar", return_value=calendar):
        await connector.create_event(
            credential=_credential(),
            secret="s",
            title="Bins out",
            start_time=datetime(2026, 10, 5, tzinfo=timezone.utc),
            end_time=datetime(2026, 10, 6, tzinfo=timezone.utc),
            is_all_day=True,
            repeat=Repeat(frequency="weekly", interval=2, count=6),
        )

    assert "RRULE:FREQ=WEEKLY;COUNT=6;INTERVAL=2" in calendar.add_event.call_args[0][0].decode()


@pytest.mark.asyncio
async def test_removing_one_date_skips_it_and_keeps_the_rest():
    event, calendar = _stored(GYM)

    await _delete(calendar, occurrence_start=THU_1_OCT, scope="this")

    event.delete.assert_not_called()
    event.save.assert_called_once()
    (main,) = _events(event.data)
    assert "EXDATE:20261001T070000Z" in event.data
    assert "UNTIL=20261224T235959Z" in event.data
    assert int(main["sequence"]) == 1


@pytest.mark.asyncio
async def test_removing_a_date_and_the_following_ends_the_series_the_moment_before():
    event, calendar = _stored(GYM.replace(";UNTIL=20261224T235959Z", ";COUNT=26"))

    await _delete(calendar, occurrence_start=THU_1_OCT, scope="following")

    event.delete.assert_not_called()
    assert "UNTIL=20261001T065959Z" in event.data
    assert "COUNT" not in event.data


@pytest.mark.asyncio
async def test_removing_from_the_first_date_on_removes_the_whole_event():
    event, calendar = _stored(GYM)

    await _delete(calendar, occurrence_start=FIRST, scope="following")

    event.delete.assert_called_once()
    event.save.assert_not_called()


@pytest.mark.asyncio
async def test_a_repeating_event_is_not_touched_without_saying_which_date():
    event, calendar = _stored(GYM)

    with pytest.raises(CalendarIntegrationException, match="occurrence_start"):
        await _delete(calendar)
    with pytest.raises(CalendarIntegrationException, match="occurrence_start"):
        await _update(calendar, title="Swim")

    event.delete.assert_not_called()
    event.save.assert_not_called()


@pytest.mark.asyncio
async def test_a_one_off_event_is_still_removed_whole():
    event, calendar = _stored(GYM.replace("RRULE:FREQ=WEEKLY;BYDAY=TU,TH;UNTIL=20261224T235959Z\r\n", ""))

    await _delete(calendar, occurrence_start=THU_1_OCT, scope="this")

    event.delete.assert_called_once()


@pytest.mark.asyncio
async def test_moving_one_date_changes_only_that_date():
    event, calendar = _stored(GYM)

    updated = await _update(
        calendar,
        start_time=datetime(2026, 10, 1, 9, 0, tzinfo=timezone.utc),
        occurrence_start=THU_1_OCT,
        scope="this",
    )

    main, moved = _events(event.data)
    assert main["dtstart"].dt == FIRST and "rrule" in main
    assert moved["recurrence-id"].dt == THU_1_OCT
    assert moved["dtstart"].dt == datetime(2026, 10, 1, 9, 0, tzinfo=timezone.utc)
    assert moved["dtend"].dt == datetime(2026, 10, 1, 10, 0, tzinfo=timezone.utc)
    assert "rrule" not in moved and str(moved["uid"]) == "gym-1"
    assert updated.id == "gym-1" and updated.repeat is None
    calendar.add_event.assert_not_called()


@pytest.mark.asyncio
async def test_changing_a_date_changed_before_edits_that_same_date():
    event, calendar = _stored(GYM)
    await _update(calendar, title="Gym with Liam", occurrence_start=THU_1_OCT)
    calendar.event_by_uid.return_value.data = event.data

    await _update(calendar, location="Studio 2", occurrence_start=THU_1_OCT)

    events = _events(event.data)
    assert len(events) == 2
    assert str(events[1]["summary"]) == "Gym with Liam" and str(events[1]["location"]) == "Studio 2"


@pytest.mark.asyncio
async def test_changing_a_date_and_the_following_splits_the_series_there():
    event, calendar = _stored(GYM)

    updated = await _update(
        calendar,
        start_time=datetime(2026, 10, 6, 7, 30, tzinfo=timezone.utc),
        occurrence_start=TUE_6_OCT,
        scope="following",
    )

    (old,) = _events(event.data)
    assert "UNTIL=20261006T065959Z" in event.data
    (new,) = _events(calendar.add_event.call_args[0][0])
    assert str(new["uid"]) not in ("gym-1", "") and str(new["uid"]) == updated.id
    assert new["dtstart"].dt == datetime(2026, 10, 6, 7, 30, tzinfo=timezone.utc)
    assert new["dtend"].dt == datetime(2026, 10, 6, 8, 30, tzinfo=timezone.utc)
    assert "UNTIL=20261224T235959Z" in new["rrule"].to_ical().decode()
    assert updated.repeat == Repeat(frequency="weekly", days=["TU", "TH"], until=date(2026, 12, 24))


@pytest.mark.asyncio
async def test_a_split_series_keeps_what_is_left_of_its_count():
    event, calendar = _stored(GYM.replace(";UNTIL=20261224T235959Z", ";COUNT=10"))

    await _update(calendar, title="Gym", occurrence_start=TUE_6_OCT, scope="following")

    (new,) = _events(calendar.add_event.call_args[0][0])
    # 29 Sep and 1 Oct came before, so 8 of the 10 are left.
    assert new["rrule"]["COUNT"] == [8]


@pytest.mark.asyncio
async def test_changing_from_the_first_date_on_edits_the_whole_series():
    event, calendar = _stored(GYM)

    await _update(
        calendar,
        title="Morning gym",
        occurrence_start=FIRST,
        scope="following",
        repeat=Repeat(frequency="weekly", days=["MO", "WE"], until=date(2026, 12, 24)),
    )

    (main,) = _events(event.data)
    assert str(main["summary"]) == "Morning gym"
    assert main["rrule"]["BYDAY"] == ["MO", "WE"]
    calendar.add_event.assert_not_called()


@pytest.mark.asyncio
async def test_a_series_in_a_time_zone_keeps_its_zone():
    zoned = GYM.replace("DTSTART:20260929T070000Z", "DTSTART;TZID=Europe/Madrid:20260929T070000").replace(
        "DTEND:20260929T080000Z", "DTEND;TZID=Europe/Madrid:20260929T080000"
    )
    event, calendar = _stored(zoned)
    madrid = ZoneInfo("Europe/Madrid")

    await _delete(calendar, occurrence_start=datetime(2026, 10, 1, 7, 0, tzinfo=madrid).astimezone(timezone.utc))

    assert "EXDATE;TZID=Europe/Madrid:20261001T070000" in event.data


@pytest.mark.asyncio
async def test_an_all_day_series_skips_a_whole_day():
    all_day = GYM.replace("DTSTART:20260929T070000Z", "DTSTART;VALUE=DATE:20260929").replace(
        "DTEND:20260929T080000Z", "DTEND;VALUE=DATE:20260930"
    ).replace("UNTIL=20261224T235959Z", "UNTIL=20261224")
    event, calendar = _stored(all_day)

    await _delete(calendar, occurrence_start=datetime(2026, 10, 1, tzinfo=timezone.utc), scope="following")
    assert "UNTIL=20260930" in event.data

    event, calendar = _stored(all_day)
    await _delete(calendar, occurrence_start=datetime(2026, 10, 1, tzinfo=timezone.utc))
    assert "EXDATE;VALUE=DATE:20261001" in event.data


@pytest.mark.asyncio
async def test_reading_says_which_dates_repeat_and_how():
    connector = CalDavCalendarConnector()
    occurrence = MagicMock()
    occurrence.data = (
        "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Example//CalDAV//EN\r\n"
        "BEGIN:VEVENT\r\nUID:gym-1\r\nDTSTAMP:20260901T000000Z\r\nRECURRENCE-ID:20261001T070000Z\r\n"
        "DTSTART:20261001T093000Z\r\nDTEND:20261001T103000Z\r\nSUMMARY:Gym\r\nEND:VEVENT\r\n"
        "END:VCALENDAR\r\n"
    )
    one_off = MagicMock()
    one_off.data = (
        "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Example//CalDAV//EN\r\n"
        "BEGIN:VEVENT\r\nUID:dentist\r\nDTSTAMP:20260901T000000Z\r\n"
        "DTSTART:20261002T150000Z\r\nDTEND:20261002T160000Z\r\nSUMMARY:Dentist\r\nEND:VEVENT\r\n"
        "END:VCALENDAR\r\n"
    )
    series = MagicMock()
    series.data = GYM
    calendar = MagicMock()
    calendar.name = "Home"
    calendar.date_search.side_effect = lambda start, end, expand: [occurrence, one_off] if expand else [series, one_off]

    with patch.object(connector, "_sync_get_client"), patch.object(connector, "_sync_get_target_calendar", return_value=calendar):
        gym, dentist = await connector.fetch_events(
            credential=_credential(),
            secret="s",
            start_time=datetime(2026, 9, 28, tzinfo=timezone.utc),
            end_time=datetime(2026, 10, 4, tzinfo=timezone.utc),
        )

    assert gym.occurrence_start == THU_1_OCT
    assert gym.start_time == datetime(2026, 10, 1, 9, 30, tzinfo=timezone.utc)
    assert gym.repeat == Repeat(frequency="weekly", days=["TU", "TH"], until=date(2026, 12, 24))
    assert dentist.repeat is None and dentist.occurrence_start is None


def test_the_tool_reads_a_repeat_the_model_gave():
    assert calendar_arguments.repeat({"frequency": "weekly", "days": ["tu", "Thursday"], "until": "2026-12-24"}) == Repeat(
        frequency="weekly", days=["TU", "TH"], until=date(2026, 12, 24)
    )
    assert calendar_arguments.repeat({"frequency": "none"}) is None
    assert calendar_arguments.repeat(None) is None
    assert calendar_arguments.scope(None) == "this"


@pytest.mark.parametrize(
    "repeat",
    [
        {"frequency": "hourly"},
        {"frequency": "daily", "days": ["MO"]},
        {"frequency": "weekly", "interval": 0},
        {"frequency": "weekly", "until": "2026-12-24", "count": 3},
        {"frequency": "weekly", "until": "christmas"},
        "weekly",
    ],
)
def test_the_tool_refuses_a_repeat_it_cannot_write(repeat):
    with pytest.raises(CalendarArgumentError):
        calendar_arguments.repeat(repeat)


def test_the_tool_refuses_a_scope_it_does_not_have():
    with pytest.raises(CalendarArgumentError):
        calendar_arguments.scope("all")
