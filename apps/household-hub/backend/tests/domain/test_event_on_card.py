"""
A remove or change card names the event it is about (#63).

The model often proposes these with only the event's ID, so the hub looks the event up and puts its
real title and time on the card. They are shown, never sent: the write runs as the model proposed it.
"""
from datetime import datetime, timedelta, timezone

from app.domain.entities.calendar_event import CalendarEvent
from app.domain.use_cases.chat.tool_approval import as_proposed, event_on_card

MADRID_SUMMER = timezone(timedelta(hours=2))

TEST_EVENT = CalendarEvent(
    id="e9f57d50",
    title="Test Event",
    start_time=datetime(2026, 9, 30, 9, 0, tzinfo=timezone.utc),
    end_time=datetime(2026, 9, 30, 9, 30, tzinfo=timezone.utc),
)

HOLIDAY = CalendarEvent(
    id="holiday",
    title="Holiday",
    start_time=datetime(2026, 10, 12, tzinfo=timezone.utc),
    end_time=datetime(2026, 10, 13, tzinfo=timezone.utc),
    is_all_day=True,
)


def test_GIVEN_a_remove_with_only_an_id_THEN_the_card_carries_the_events_title_and_time_in_the_members_zone():
    shown = event_on_card("delete", {"action": "delete", "event_id": "e9f57d50"}, TEST_EVENT, "Europe/Madrid")

    assert shown == {
        "title": "Test Event",
        "start_time": "2026-09-30T11:00:00+02:00",
        "end_time": "2026-09-30T11:30:00+02:00",
        "is_all_day": False,
    }


def test_GIVEN_a_remove_naming_another_title_THEN_the_events_real_title_wins():
    shown = event_on_card("delete", {"action": "delete", "event_id": "e9f57d50", "title": "Dentist"}, TEST_EVENT, None)

    assert shown["title"] == "Test Event"


def test_GIVEN_no_zone_the_hub_can_use_THEN_the_time_keeps_the_calendars_own_offset():
    event = CalendarEvent(
        id="e1",
        title="Lunch",
        start_time=datetime(2026, 9, 30, 13, 0, tzinfo=MADRID_SUMMER),
        end_time=datetime(2026, 9, 30, 14, 0, tzinfo=MADRID_SUMMER),
    )

    shown = event_on_card("delete", {"action": "delete", "event_id": "e1"}, event, "Not/AZone")

    assert shown["start_time"] == "2026-09-30T13:00:00+02:00"


def test_GIVEN_an_all_day_event_THEN_the_card_carries_its_days_not_a_midnight():
    shown = event_on_card("delete", {"action": "delete", "event_id": "holiday"}, HOLIDAY, "America/Lima")

    assert shown == {"title": "Holiday", "start_time": "2026-10-12", "end_time": "2026-10-13", "is_all_day": True}


def test_GIVEN_a_change_that_only_moves_the_event_THEN_the_card_adds_its_title_but_keeps_the_new_time():
    arguments = {"action": "update", "event_id": "e9f57d50", "start_time": "2026-10-01T10:00:00+02:00"}

    assert event_on_card("update", arguments, TEST_EVENT, "Europe/Madrid") == {"title": "Test Event"}


def test_GIVEN_a_change_that_only_renames_the_event_THEN_the_card_keeps_the_new_title_and_adds_its_time():
    arguments = {"action": "update", "event_id": "e9f57d50", "title": "Team sync"}

    assert event_on_card("update", arguments, TEST_EVENT, "Europe/Madrid") == {
        "start_time": "2026-09-30T11:00:00+02:00",
        "end_time": "2026-09-30T11:30:00+02:00",
        "is_all_day": False,
    }


def test_GIVEN_an_add_THEN_nothing_is_looked_up_onto_its_card():
    assert event_on_card("create", {"action": "create", "title": "Dinner"}, TEST_EVENT, None) == {}


def test_the_call_that_runs_drops_what_was_only_shown_but_keeps_what_the_model_or_member_set():
    looked_up = {"title": "Test Event", "start_time": "2026-09-30T11:00:00+02:00"}
    on_card = {"action": "update", "event_id": "e9f57d50", "end_time": "2026-09-30T12:00:00+02:00", **looked_up}

    assert as_proposed(on_card, looked_up) == {
        "action": "update",
        "event_id": "e9f57d50",
        "end_time": "2026-09-30T12:00:00+02:00",
    }

    edited = {**on_card, "title": "Renamed on the card"}
    assert as_proposed(edited, looked_up)["title"] == "Renamed on the card"
