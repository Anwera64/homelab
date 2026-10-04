"""Repeating calendar events over CalDAV: making them, and changing or removing one date or the rest."""

import re
from datetime import date, datetime, timezone
from unittest.mock import MagicMock, patch
from zoneinfo import ZoneInfo

import pytest
from caldav.lib.error import NotFoundError
from icalendar import Calendar as ICalendar

from app.data.connectors.caldav_calendar_connector import CalDavCalendarConnector
from app.domain.entities.calendar_event import Repeat
from app.domain.entities.integration_credential import CalendarCredential
from app.domain.exceptions import CalendarIntegrationException, CalendarWriteNotConfirmedException
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


class _StoredEvent:
    """One event on the fake calendar, with the calls a test counts."""

    def __init__(self, calendar, uid, data):
        self.calendar, self.uid, self.data = calendar, uid, data
        self.save = MagicMock(side_effect=self._save)
        self.delete = MagicMock(side_effect=self._delete)

    def _save(self):
        kept = self.calendar.keeps(self.data)
        if kept is None:
            self.calendar.events.pop(self.uid, None)
        else:
            self.data = kept

    def _delete(self):
        if not self.calendar.keeps_deleted:
            self.calendar.events.pop(self.uid, None)


class FakeCalendar:
    """
    A calendar that holds what is written to it, so reading an event back shows what a real server
    would. `keeps` is what the server makes of each write (None: it drops a new event), and
    `keeps_deleted` a server that answers a removal without removing.
    """

    name = "Home"

    def __init__(self):
        self.events = {}
        self.keeps = lambda data: data
        self.keeps_deleted = False
        self.add_event = MagicMock(side_effect=self._add)
        self.event_by_uid = MagicMock(side_effect=self._find)

    def put(self, data):
        uid = str(next(iter(ICalendar.from_ical(data).walk("VEVENT")))["uid"])
        self.events[uid] = _StoredEvent(self, uid, data)
        return self.events[uid]

    def _add(self, ical):
        kept = self.keeps(ical.decode() if isinstance(ical, (bytes, bytearray)) else str(ical))
        if kept is not None:
            self.put(kept)

    def _find(self, uid):
        if uid not in self.events:
            raise NotFoundError(f"no event {uid}")
        return self.events[uid]


def _stored(data):
    calendar = FakeCalendar()
    return calendar.put(data), calendar


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
    calendar = FakeCalendar()
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
    calendar = FakeCalendar()
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
        calendar_arguments.scope("everything")


def test_the_tool_takes_the_whole_series_as_a_scope():
    assert calendar_arguments.scope("all") == "all"


def test_the_tool_tells_the_model_it_can_mean_the_whole_series():
    from app.domain.use_cases.integrations.list_available_tools import ListAvailableToolsUseCase

    tools = ListAvailableToolsUseCase().execute()
    write = next(tool for tool in tools if tool.name == "calendar_write").parameters_schema["properties"]

    assert write["scope"]["enum"] == ["this", "following", "all"]
    assert "whole" in write["scope"]["description"] and "occurrence_start" in write["scope"]["description"]

# --- The hub checks the agent's dates against the series itself -------------------------------

from app.data.connectors import ical_series  # noqa: E402

# Grocery shop on Thursdays at 17:30 Madrid time (+02:00 until 25 Oct) from Thu 8 Oct 2026.
SHOP = (
    "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Example//CalDAV//EN\r\n"
    "BEGIN:VEVENT\r\nUID:gym-1\r\nDTSTAMP:20261004T000000Z\r\n"
    "DTSTART;TZID=Europe/Madrid:20261008T173000\r\nDTEND;TZID=Europe/Madrid:20261008T183000\r\n"
    "SUMMARY:Weekly grocery shop\r\nRRULE:FREQ=WEEKLY;BYDAY=TH\r\n"
    "END:VEVENT\r\nEND:VCALENDAR\r\n"
)
MADRID = ZoneInfo("Europe/Madrid")
SHOP_8_OCT = datetime(2026, 10, 8, 17, 30, tzinfo=MADRID)


def _shop(data=SHOP):
    return ICalendar.from_ical(data)


@pytest.mark.parametrize(
    "said",
    [
        datetime(2026, 10, 8, 17, 30, tzinfo=MADRID),
        datetime(2026, 10, 8, 15, 30, tzinfo=timezone.utc),  # the same instant
        datetime(2026, 10, 8, 17, 30, tzinfo=timezone.utc),  # the local time, wrongly called UTC
        datetime(2026, 10, 8, 17, 30),  # no zone at all
    ],
)
def test_a_date_the_agent_names_is_matched_to_the_real_date_of_the_series(said):
    found = ical_series.occurrence_at(_shop(), said)

    assert found == SHOP_8_OCT
    assert found.utcoffset() == SHOP_8_OCT.utcoffset()


def test_a_moment_that_is_no_date_of_the_series_matches_nothing():
    assert ical_series.occurrence_at(_shop(), datetime(2026, 10, 7, 17, 30, tzinfo=MADRID)) is None
    assert ical_series.occurrence_at(_shop(), datetime(2026, 10, 8, 9, 0, tzinfo=MADRID)) is None
    assert ical_series.occurrence_at(_shop(), datetime(2026, 10, 1, 17, 30, tzinfo=MADRID)) is None


def test_a_date_already_skipped_is_no_longer_a_date_of_the_series():
    skipped = _shop(SHOP.replace("RRULE:", "EXDATE;TZID=Europe/Madrid:20261015T173000\r\nRRULE:"))

    assert ical_series.occurrence_at(skipped, datetime(2026, 10, 15, 17, 30, tzinfo=MADRID)) is None
    assert ical_series.occurrence_at(skipped, SHOP_8_OCT) == SHOP_8_OCT


def test_a_skip_written_for_the_wrong_time_skips_nothing():
    # What Google held on 4 Oct 2026: the skip two hours after the date it was meant for.
    stale = _shop(SHOP.replace("RRULE:", "EXDATE;TZID=Europe/Madrid:20261008T193000\r\nRRULE:"))

    assert ical_series.dates(stale, SHOP_8_OCT, 2) == [SHOP_8_OCT, datetime(2026, 10, 15, 17, 30, tzinfo=MADRID)]


def test_an_all_day_series_is_matched_by_its_day():
    bins = _shop(
        SHOP.replace("DTSTART;TZID=Europe/Madrid:20261008T173000", "DTSTART;VALUE=DATE:20261008").replace(
            "DTEND;TZID=Europe/Madrid:20261008T183000", "DTEND;VALUE=DATE:20261009"
        )
    )

    assert ical_series.occurrence_at(bins, datetime(2026, 10, 15, tzinfo=timezone.utc)) == date(2026, 10, 15)
    assert ical_series.occurrence_at(bins, datetime(2026, 10, 14, tzinfo=timezone.utc)) is None


def test_the_dates_around_a_moment_are_the_one_before_and_the_ones_after():
    around = ical_series.nearby(_shop(), datetime(2026, 10, 20, 12, 0, tzinfo=MADRID))

    assert around == [datetime(2026, 10, day, 17, 30, tzinfo=MADRID) for day in (15, 22, 29)]


def test_a_series_that_ended_has_no_dates_after_its_end():
    ended = _shop(SHOP.replace("BYDAY=TH", "BYDAY=TH;UNTIL=20261015T153000Z"))

    assert ical_series.dates(ended, datetime(2026, 10, 16, tzinfo=timezone.utc), 3) == []
    assert len(ical_series.dates(ended, SHOP_8_OCT, 5)) == 2


# --- The connector checks the agent's date before it writes ----------------------------------


@pytest.mark.asyncio
async def test_removing_a_date_named_in_the_wrong_zone_skips_the_real_date():
    event, calendar = _stored(SHOP)

    await _delete(calendar, occurrence_start=datetime(2026, 10, 8, 17, 30, tzinfo=timezone.utc), scope="this")

    assert "EXDATE;TZID=Europe/Madrid:20261008T173000" in event.data
    assert "T193000" not in event.data


@pytest.mark.asyncio
async def test_changing_a_date_named_in_the_wrong_zone_changes_the_real_date():
    event, calendar = _stored(SHOP)

    await _update(calendar, title="Big shop", occurrence_start=datetime(2026, 10, 8, 17, 30, tzinfo=timezone.utc))

    _, changed = _events(event.data)
    assert changed["recurrence-id"].dt == SHOP_8_OCT
    assert changed["dtstart"].dt == SHOP_8_OCT


@pytest.mark.asyncio
@pytest.mark.parametrize("scope", ["this", "following"])
async def test_a_date_the_series_does_not_have_is_refused_with_the_dates_it_does_have(scope):
    event, calendar = _stored(SHOP)
    wednesday = datetime(2026, 10, 14, 17, 30, tzinfo=MADRID)

    with pytest.raises(CalendarIntegrationException, match="2026-10-08T17:30.*2026-10-15T17:30"):
        await _delete(calendar, occurrence_start=wednesday, scope=scope)
    with pytest.raises(CalendarIntegrationException, match="2026-10-08T17:30.*2026-10-15T17:30"):
        await _update(calendar, title="Big shop", occurrence_start=wednesday, scope=scope)

    event.save.assert_not_called()
    event.delete.assert_not_called()
    calendar.add_event.assert_not_called()


@pytest.mark.asyncio
async def test_removing_an_event_the_calendar_does_not_have_says_so():
    event, calendar = _stored(SHOP)
    calendar.event_by_uid.side_effect = NotFoundError("no such event")

    with pytest.raises(CalendarIntegrationException, match="not found"):
        await _delete(calendar, occurrence_start=SHOP_8_OCT)


# --- The connector reads the calendar back after it writes -----------------------------------


@pytest.mark.asyncio
async def test_a_skip_the_calendar_did_not_keep_is_not_reported_as_done():
    event, calendar = _stored(SHOP)
    calendar.keeps = lambda data: re.sub(r"EXDATE[^\r\n]*\r?\n", "", data)

    with pytest.raises(CalendarWriteNotConfirmedException, match="2026-10-08"):
        await _delete(calendar, occurrence_start=SHOP_8_OCT)


@pytest.mark.asyncio
async def test_an_event_still_there_after_removing_it_is_not_reported_as_done():
    event, calendar = _stored(GYM.replace("RRULE:FREQ=WEEKLY;BYDAY=TU,TH;UNTIL=20261224T235959Z\r\n", ""))
    calendar.keeps_deleted = True

    with pytest.raises(CalendarWriteNotConfirmedException):
        await _delete(calendar)


@pytest.mark.asyncio
async def test_a_series_the_calendar_did_not_end_is_not_reported_as_done():
    event, calendar = _stored(SHOP)
    calendar.keeps = lambda data: SHOP

    with pytest.raises(CalendarWriteNotConfirmedException):
        await _delete(calendar, occurrence_start=datetime(2026, 10, 15, 17, 30, tzinfo=MADRID), scope="following")


async def _create(calendar, **event):
    connector = CalDavCalendarConnector()
    with patch.object(connector, "_sync_get_client"), patch.object(connector, "_sync_get_target_calendar", return_value=calendar):
        return await connector.create_event(credential=_credential(), secret="s", **event)


GYM_AT_7 = dict(title="Gym", start_time=FIRST, end_time=datetime(2026, 9, 29, 8, 0, tzinfo=timezone.utc))


@pytest.mark.asyncio
async def test_a_new_event_the_calendar_stored_at_another_time_is_not_reported_as_done():
    calendar = FakeCalendar()
    calendar.keeps = lambda data: data.replace("DTSTART:20260929T070000Z", "DTSTART:20260929T090000Z")

    with pytest.raises(CalendarWriteNotConfirmedException):
        await _create(calendar, **GYM_AT_7)


@pytest.mark.asyncio
async def test_a_new_event_the_calendar_dropped_is_not_reported_as_done():
    calendar = FakeCalendar()
    calendar.keeps = lambda data: None

    with pytest.raises(CalendarWriteNotConfirmedException):
        await _create(calendar, **GYM_AT_7)


@pytest.mark.asyncio
async def test_a_new_repeating_event_stored_without_its_repeat_is_not_reported_as_done():
    calendar = FakeCalendar()
    calendar.keeps = lambda data: re.sub(r"RRULE[^\r\n]*\r?\n", "", data)

    with pytest.raises(CalendarWriteNotConfirmedException):
        await _create(calendar, repeat=Repeat(frequency="weekly", days=["TU", "TH"]), **GYM_AT_7)


@pytest.mark.asyncio
async def test_a_calendar_that_only_rewrites_the_zone_has_taken_the_event():
    # Google names a fixed +02:00 offset after a place that keeps it, as it did with Africa/Maputo.
    calendar = FakeCalendar()
    calendar.keeps = lambda data: data.replace("DTSTART:20260929T070000Z", "DTSTART;TZID=Africa/Maputo:20260929T090000").replace(
        "DTEND:20260929T080000Z", "DTEND;TZID=Africa/Maputo:20260929T100000"
    )

    created = await _create(calendar, repeat=Repeat(frequency="weekly", days=["TU", "TH"]), **GYM_AT_7)

    assert created.title == "Gym"


@pytest.mark.asyncio
async def test_a_change_the_calendar_did_not_keep_is_not_reported_as_done():
    one_off = GYM.replace("RRULE:FREQ=WEEKLY;BYDAY=TU,TH;UNTIL=20261224T235959Z\r\n", "")
    event, calendar = _stored(one_off)
    calendar.keeps = lambda data: one_off

    with pytest.raises(CalendarWriteNotConfirmedException, match="Gym"):
        await _update(calendar, title="Swim")


@pytest.mark.asyncio
async def test_a_changed_date_the_calendar_did_not_keep_is_not_reported_as_done():
    event, calendar = _stored(SHOP)
    calendar.keeps = lambda data: SHOP

    with pytest.raises(CalendarWriteNotConfirmedException):
        await _update(calendar, title="Big shop", occurrence_start=SHOP_8_OCT)


@pytest.mark.asyncio
async def test_a_split_whose_new_half_the_calendar_dropped_is_not_reported_as_done():
    event, calendar = _stored(SHOP)
    calendar.keeps = lambda data: data if "UID:gym-1" in data else None

    with pytest.raises(CalendarWriteNotConfirmedException):
        await _update(
            calendar, title="Big shop", occurrence_start=datetime(2026, 10, 15, 17, 30, tzinfo=MADRID), scope="following"
        )


# --- The whole series, whatever was done to it before ---------------------------------------

# Stretch on Wednesdays at 14:00 Madrid time from 7 Oct 2026, as the manual test left it: the first
# date removed, and the second moved an hour later.
STRETCH = (
    "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Example//CalDAV//EN\r\n"
    "BEGIN:VEVENT\r\nUID:gym-1\r\nDTSTAMP:20261004T000000Z\r\n"
    "DTSTART;TZID=Europe/Madrid:20261007T140000\r\nDTEND;TZID=Europe/Madrid:20261007T143000\r\n"
    "SUMMARY:Stretch\r\nRRULE:FREQ=WEEKLY;BYDAY=WE\r\nEXDATE;TZID=Europe/Madrid:20261007T140000\r\n"
    "END:VEVENT\r\n"
    "BEGIN:VEVENT\r\nUID:gym-1\r\nDTSTAMP:20261004T000000Z\r\n"
    "RECURRENCE-ID;TZID=Europe/Madrid:20261014T140000\r\n"
    "DTSTART;TZID=Europe/Madrid:20261014T150000\r\nDTEND;TZID=Europe/Madrid:20261014T153000\r\n"
    "SUMMARY:Stretch\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
)


def _wed(day, hour, minute=0):
    return datetime(2026, 10, day, hour, minute, tzinfo=MADRID)


@pytest.mark.asyncio
async def test_removing_the_whole_series_needs_no_date_and_removes_the_event():
    event, calendar = _stored(STRETCH)

    await _delete(calendar, scope="all")

    event.delete.assert_called_once()
    event.save.assert_not_called()
    assert calendar.events == {}


@pytest.mark.asyncio
async def test_a_repeating_event_named_without_a_date_is_told_it_can_mean_the_whole_series():
    event, calendar = _stored(STRETCH)

    with pytest.raises(CalendarIntegrationException, match="'all'"):
        await _delete(calendar)


@pytest.mark.asyncio
async def test_renaming_the_whole_series_renames_the_dates_changed_on_their_own_too():
    event, calendar = _stored(STRETCH)

    updated = await _update(calendar, title="Yoga", scope="all")

    assert [str(entry["summary"]) for entry in _events(event.data)] == ["Yoga", "Yoga"]
    assert updated.id == "gym-1" and updated.title == "Yoga"
    calendar.add_event.assert_not_called()


@pytest.mark.asyncio
async def test_moving_the_whole_series_to_another_hour_keeps_what_was_skipped_and_what_was_moved():
    event, calendar = _stored(STRETCH)

    # The model names the new hour on whichever date it has in mind; the series keeps its own first day.
    await _update(calendar, start_time=_wed(21, 16), scope="all")

    main, moved = _events(event.data)
    assert main["dtstart"].dt == _wed(7, 16) and main["dtend"].dt == _wed(7, 16, 30)
    assert ical_series.dates(ICalendar.from_ical(event.data), _wed(1, 0), 2) == [_wed(14, 16), _wed(21, 16)]
    assert moved["recurrence-id"].dt == _wed(14, 16)
    assert moved["dtstart"].dt == _wed(14, 15)
    calendar.add_event.assert_not_called()


@pytest.mark.asyncio
async def test_giving_the_whole_series_a_new_length_sets_it_from_the_new_times():
    event, calendar = _stored(STRETCH)

    await _update(calendar, start_time=_wed(21, 16), end_time=_wed(21, 17), scope="all")

    main, _ = _events(event.data)
    assert main["dtstart"].dt == _wed(7, 16) and main["dtend"].dt == _wed(7, 17)


# --- A date moved on its own is the same date by either time ----------------------------------


def test_a_moved_date_is_found_by_its_own_time_or_the_time_it_was_moved_to():
    stretch = ICalendar.from_ical(STRETCH)

    assert ical_series.occurrence_at(stretch, _wed(14, 14)) == _wed(14, 14)
    assert ical_series.occurrence_at(stretch, _wed(14, 15)) == _wed(14, 14)
    assert ical_series.occurrence_at(stretch, datetime(2026, 10, 14, 15, 0)) == _wed(14, 14)
    assert ical_series.occurrence_at(stretch, _wed(21, 15)) is None


@pytest.mark.asyncio
async def test_removing_a_moved_date_named_by_its_new_time_removes_that_date():
    event, calendar = _stored(STRETCH)

    await _delete(calendar, occurrence_start=datetime(2026, 10, 14, 15, 0), scope="this")

    (main,) = _events(event.data)
    assert ical_series.dates(ICalendar.from_ical(event.data), _wed(1, 0), 1) == [_wed(21, 14)]


# --- Removing the last dates of a series removes the event ------------------------------------


@pytest.mark.asyncio
async def test_removing_from_the_first_date_still_there_removes_the_event():
    event, calendar = _stored(STRETCH)

    await _delete(calendar, occurrence_start=_wed(14, 14), scope="following")

    event.delete.assert_called_once()
    event.save.assert_not_called()
    assert calendar.events == {}


@pytest.mark.asyncio
async def test_removing_the_only_date_left_removes_the_event():
    two = STRETCH.replace("RRULE:FREQ=WEEKLY;BYDAY=WE", "RRULE:FREQ=WEEKLY;BYDAY=WE;COUNT=2")
    event, calendar = _stored(two)

    await _delete(calendar, occurrence_start=_wed(14, 14), scope="this")

    event.delete.assert_called_once()
    event.save.assert_not_called()


@pytest.mark.asyncio
async def test_removing_the_following_dates_keeps_an_event_that_still_has_one():
    kept_first = STRETCH.replace("EXDATE;TZID=Europe/Madrid:20261007T140000\r\n", "")
    event, calendar = _stored(kept_first)

    await _delete(calendar, occurrence_start=_wed(14, 14), scope="following")

    event.delete.assert_not_called()
    assert ical_series.dates(ICalendar.from_ical(event.data), _wed(1, 0), 3) == [_wed(7, 14)]


@pytest.mark.asyncio
async def test_removing_the_last_dates_is_done_on_a_calendar_that_drops_an_event_with_no_dates():
    # Google keeps no event without dates. On 4 Oct 2026 this came back as "does not have it after writing".
    event, calendar = _stored(STRETCH)
    calendar.keeps = lambda data: data if ical_series.dates(ICalendar.from_ical(data), _wed(1, 0), 1) else None

    assert await _delete(calendar, occurrence_start=_wed(14, 14), scope="following") is True
    assert calendar.events == {}

# --- A new event is stored in the member's zone, not at a fixed offset -----------------------


@pytest.mark.asyncio
async def test_a_new_event_is_stored_in_a_named_zone_with_its_definition():
    calendar = FakeCalendar()

    await _create(
        calendar,
        title="Zone check",
        start_time=datetime(2026, 10, 9, 9, 0, tzinfo=MADRID),
        end_time=datetime(2026, 10, 9, 9, 30, tzinfo=MADRID),
        repeat=Repeat(frequency="weekly", days=["FR"]),
    )

    written = calendar.add_event.call_args[0][0].decode()
    assert "DTSTART;TZID=Europe/Madrid:20261009T090000" in written
    assert "BEGIN:VTIMEZONE" in written and "TZID:Europe/Madrid" in written


@pytest.mark.asyncio
async def test_a_series_in_a_named_zone_keeps_its_hour_when_the_clocks_change():
    calendar = FakeCalendar()
    created = await _create(
        calendar,
        title="Zone check",
        start_time=datetime(2026, 10, 9, 9, 0, tzinfo=MADRID),
        end_time=datetime(2026, 10, 9, 9, 30, tzinfo=MADRID),
        repeat=Repeat(frequency="weekly", days=["FR"]),
    )

    stored = ICalendar.from_ical(calendar.events[created.id].data)
    before, after = ical_series.dates(stored, datetime(2026, 10, 23, tzinfo=MADRID), 2)

    assert (before.astimezone(MADRID).hour, after.astimezone(MADRID).hour) == (9, 9)
    assert after.astimezone(timezone.utc).hour == 8, "09:00 in Madrid is 08:00 UTC once the clocks go back"


def test_a_series_at_a_fixed_offset_drifts_an_hour_when_the_clocks_change():
    # What the hub stored until 4 Oct 2026, and Google filed under Africa/Maputo.
    fixed = _shop(SHOP.replace("TZID=Europe/Madrid", "TZID=Africa/Maputo"))

    _, after = ical_series.dates(fixed, datetime(2026, 10, 22, tzinfo=MADRID), 2)

    assert after.astimezone(MADRID).hour == 16, "17:30 becomes 16:30 in Madrid from 25 Oct"


def test_the_tool_tells_the_model_to_write_the_members_clock_time():
    from app.domain.use_cases.integrations.list_available_tools import ListAvailableToolsUseCase

    tools = {tool.name: tool.parameters_schema["properties"] for tool in ListAvailableToolsUseCase().execute()}

    for tool in ("calendar_read", "calendar_write"):
        for field in ("start_time", "end_time"):
            said = tools[tool][field]["description"]
            assert "local" in said, f"{tool}.{field}: {said}"
            assert not re.search(r"\d{2}:\d{2}:\d{2}Z", said), f"{tool}.{field} shows a UTC example: {said}"
    assert "local" in tools["calendar_write"]["occurrence_start"]["description"]
