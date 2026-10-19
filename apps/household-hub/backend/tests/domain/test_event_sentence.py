"""A memory the hub rebuilt from its calendar event (memory spec §7): stored as the event's title and
its start in UTC, with the time added by whoever reads it, in their own zone."""

from datetime import datetime, timezone

from app.domain.entities.memory import ALL_DAY, TIMED, AgentMemory
from app.domain.use_cases.memories.event_sentence import event_sentence, memory_text

START = datetime(2026, 10, 19, 17, 30, tzinfo=timezone.utc)


def test_GIVEN_a_timed_event_THEN_the_sentence_names_the_day_and_time_in_the_zone_given():
    assert event_sentence("Dinner together", START, TIMED, "Europe/Madrid") == (
        "Dinner together on Monday 19 October, 19:30."
    )


def test_GIVEN_a_zone_across_midnight_THEN_the_day_is_the_one_in_that_zone():
    late = datetime(2026, 10, 19, 23, 30, tzinfo=timezone.utc)
    assert event_sentence("Call home", late, TIMED, "Asia/Tokyo") == "Call home on Tuesday 20 October, 08:30."


def test_GIVEN_no_zone_or_an_unknown_one_THEN_the_time_is_UTC_and_says_so():
    assert event_sentence("Dinner together", START, TIMED, None) == "Dinner together on Monday 19 October, 17:30 UTC."
    assert event_sentence("Dinner together", START, TIMED, "Mars/Base") == (
        "Dinner together on Monday 19 October, 17:30 UTC."
    )


def test_GIVEN_an_all_day_event_THEN_its_date_is_the_same_in_every_zone_and_has_no_time():
    day = datetime(2026, 10, 24, 0, 0, tzinfo=timezone.utc)
    assert event_sentence("Studio jury", day, ALL_DAY, "America/Lima") == "Studio jury on Saturday 24 October."


def test_GIVEN_a_title_that_already_ends_with_a_full_stop_THEN_it_is_not_doubled():
    day = datetime(2026, 10, 24, tzinfo=timezone.utc)
    assert event_sentence("Studio jury.", day, ALL_DAY, None) == "Studio jury on Saturday 24 October."


def test_GIVEN_a_naive_start_from_SQLite_THEN_it_is_read_as_UTC():
    naive = datetime(2026, 10, 19, 17, 30)
    assert event_sentence("Dinner together", naive, TIMED, "Europe/Madrid") == (
        "Dinner together on Monday 19 October, 19:30."
    )


def test_GIVEN_a_memory_whose_time_comes_from_its_event_THEN_its_text_adds_the_time():
    memory = AgentMemory(content="Dinner together", event_starts_at=START, time_from_event=TIMED)
    assert memory_text(memory, "Europe/Madrid") == "Dinner together on Monday 19 October, 19:30."


def test_GIVEN_a_memory_the_model_worded_THEN_its_text_is_its_content_even_when_linked():
    memory = AgentMemory(content="Dinner with Liam's parents on Saturday.", event_id="e1", event_starts_at=START)
    assert memory_text(memory, "Europe/Madrid") == "Dinner with Liam's parents on Saturday."
