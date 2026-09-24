"""
The line that tells an agent what day it is (#39).

Without it the model answers from its training cutoff: "latest" is measured from the wrong year and
milestones are given expiry dates that have already passed. The phone says which timezone it is in;
the hub only trusts that far, and says UTC out loud when it can't.
"""
from datetime import datetime, timezone

import pytest

from app.domain.use_cases.chat.current_date_line import current_date_line, sent_at_stamp


NOW = datetime(2026, 9, 24, 20, 5, 42, tzinfo=timezone.utc)


def test_the_line_is_in_the_phones_timezone():
    assert current_date_line(NOW, "America/Mexico_City") == (
        "Today is Thursday, 24 September 2026, 14:05 (America/Mexico_City)."
    )


def test_the_date_moves_with_the_timezone():
    assert current_date_line(NOW, "Asia/Tokyo") == (
        "Today is Friday, 25 September 2026, 05:05 (Asia/Tokyo)."
    )


@pytest.mark.parametrize("unusable", [None, "", "Mars/Olympus", "../etc/passwd", "A" * 200])
def test_an_unusable_timezone_falls_back_to_utc_and_says_so(unusable):
    assert current_date_line(NOW, unusable) == "Today is Thursday, 24 September 2026, 20:05 (UTC)."


def test_seconds_never_appear_so_the_prompt_is_stable_within_a_minute():
    later_that_minute = NOW.replace(second=59)
    assert current_date_line(NOW, "UTC") == current_date_line(later_that_minute, "UTC")


def test_a_message_is_stamped_with_when_it_was_sent_in_the_phones_timezone():
    sent = datetime(2026, 9, 21, 14, 14, tzinfo=timezone.utc)
    assert sent_at_stamp(sent, "America/Mexico_City") == "Mon 21 Sep 2026, 08:14"


def test_a_time_stored_without_a_zone_is_read_as_utc():
    """SQLite hands created_at back without its zone; it was written in UTC."""
    naive = datetime(2026, 9, 21, 14, 14)
    assert sent_at_stamp(naive, "Asia/Tokyo") == "Mon 21 Sep 2026, 23:14"
    assert current_date_line(naive, "Asia/Tokyo") == "Today is Monday, 21 September 2026, 23:14 (Asia/Tokyo)."


def test_a_stamp_with_an_unusable_timezone_is_in_utc():
    sent = datetime(2026, 9, 21, 14, 14, tzinfo=timezone.utc)
    assert sent_at_stamp(sent, "Mars/Olympus") == "Mon 21 Sep 2026, 14:14"
