"""
The line that tells an agent what day it is (#39).

Without it the model answers from its training cutoff: "latest" is measured from the wrong year and
milestones are given expiry dates that have already passed. The phone says which timezone it is in;
the hub only trusts that far, and says UTC out loud when it can't.
"""
from datetime import datetime, timezone

import pytest

from app.domain.use_cases.chat.current_date_line import current_date_line


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
