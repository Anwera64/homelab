from dataclasses import dataclass, field
from datetime import date, datetime
from typing import List, Optional


FREQUENCIES = ("daily", "weekly", "monthly", "yearly")
WEEKDAYS = ("MO", "TU", "WE", "TH", "FR", "SA", "SU")

# Which dates of a repeating event a change or removal is for.
ONLY_THIS = "this"
THIS_AND_FOLLOWING = "following"
SCOPES = (ONLY_THIS, THIS_AND_FOLLOWING)


@dataclass
class Repeat:
    """
    How an event repeats, in the words the tools and the phone use: every [interval] days, weeks,
    months or years, on [days] for a weekly one, ending on [until] or after [count] times, or never.
    """

    frequency: str
    interval: int = 1
    days: List[str] = field(default_factory=list)
    until: Optional[date] = None
    count: Optional[int] = None

    def to_dict(self) -> dict:
        repeat: dict = {"frequency": self.frequency, "interval": self.interval}
        if self.days:
            repeat["days"] = list(self.days)
        if self.until:
            repeat["until"] = self.until.isoformat()
        if self.count:
            repeat["count"] = self.count
        return repeat


@dataclass
class CalendarEvent:
    id: str
    title: str
    start_time: datetime
    end_time: datetime
    description: str = ""
    location: str = ""
    is_all_day: bool = False
    calendar_name: str = ""
    # Set on every date of a repeating event: the series' rule, and which date of it this one is.
    # A date that was moved on its own starts at [start_time] but is still named by [occurrence_start].
    repeat: Optional[Repeat] = None
    occurrence_start: Optional[datetime] = None
