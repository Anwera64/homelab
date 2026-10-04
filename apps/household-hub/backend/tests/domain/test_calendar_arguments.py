"""Times the model writes for the calendar are clock time in the member's zone, whatever zone it attached."""

from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo

import pytest

from app.domain.use_cases.integrations import calendar_arguments

MADRID = ZoneInfo("Europe/Madrid")


@pytest.mark.parametrize(
    "written",
    ["2026-10-09T15:00:00Z", "2026-10-09T15:00:00+02:00", "2026-10-09T15:00:00-05:00", "2026-10-09T15:00:00"],
)
def test_GIVEN_a_member_zone_WHEN_a_time_is_read_THEN_it_is_that_clock_time_in_the_zone(written):
    read = calendar_arguments.moment(written, "start_time", MADRID)

    assert read == datetime(2026, 10, 9, 15, 0, tzinfo=MADRID)
    assert read.tzinfo is MADRID, "a named zone, so a repeating event follows its clock changes"


def test_GIVEN_a_member_zone_WHEN_times_either_side_of_a_clock_change_are_read_THEN_each_has_that_days_offset():
    summer = calendar_arguments.moment("2026-10-23T09:00:00Z", "start_time", MADRID)
    winter = calendar_arguments.moment("2026-10-30T09:00:00Z", "start_time", MADRID)

    assert summer.utcoffset() == timedelta(hours=2)
    assert winter.utcoffset() == timedelta(hours=1)


def test_GIVEN_no_member_zone_WHEN_a_time_is_read_THEN_it_is_taken_as_written():
    assert calendar_arguments.moment("2026-10-09T15:00:00Z", "start_time") == datetime(2026, 10, 9, 15, tzinfo=timezone.utc)
    assert calendar_arguments.moment("2026-10-09T15:00:00", "start_time", None) == datetime(2026, 10, 9, 15)


def test_GIVEN_a_zone_name_WHEN_it_is_looked_up_THEN_only_a_real_one_gives_a_zone():
    assert calendar_arguments.zone_of("Europe/Madrid") == MADRID
    assert calendar_arguments.zone_of("Mars/Olympus") is None
    assert calendar_arguments.zone_of(None) is None


def test_GIVEN_nothing_written_WHEN_a_time_is_read_THEN_there_is_none():
    assert calendar_arguments.moment(None, "occurrence_start", MADRID) is None
