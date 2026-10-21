from dataclasses import dataclass


@dataclass(frozen=True)
class DiscoveredCalendar:
    """A calendar of the member's account, as the account lists it."""

    url: str
    name: str
    writable: bool


@dataclass(frozen=True)
class CalendarSource:
    """
    A calendar of the member's account as the hub keeps it: whether it is read ([picked]), and the
    short [key] the model names it by, since a calendar's address is long and easy to copy wrong.
    """

    key: str
    url: str
    name: str
    writable: bool
    picked: bool
