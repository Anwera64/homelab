from dataclasses import dataclass
from datetime import datetime


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
