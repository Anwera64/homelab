from datetime import datetime, timedelta, timezone
from typing import Dict, List, Optional

import pytest

from app.domain.entities.calendar_event import CalendarEvent
from app.domain.entities.calendar_source import CalendarSource, DiscoveredCalendar
from app.domain.entities.integration_credential import CalendarCredential
from app.domain.exceptions import CalendarIntegrationException, CalendarUnreachableException
from app.domain.use_cases.integrations.calendar_sources import SyncCalendarSourcesUseCase
from app.domain.use_cases.integrations.get_calendar_events import GetCalendarEventsUseCase

MORNING = datetime(2026, 10, 22, 9, tzinfo=timezone.utc)
HOME = DiscoveredCalendar(url="https://cal.example/home/", name="Home", writable=True)
WORK = DiscoveredCalendar(url="https://cal.example/work/", name="Work", writable=True)
HOLIDAYS = DiscoveredCalendar(url="https://cal.example/holidays/", name="Holidays", writable=False)


def _event(title: str, hours_later: int) -> CalendarEvent:
    start = MORNING + timedelta(hours=hours_later)
    return CalendarEvent(id=f"{title}-uid", title=title, start_time=start, end_time=start + timedelta(hours=1))


class FakeCredentials:
    def __init__(self):
        self.credential = CalendarCredential(
            id="cred-1", user_id="member-1", provider="apple_icloud", url="https://cal.example/",
            username="m", encrypted_secret="x",
        )

    async def get_by_user_id(self, user_id: str) -> Optional[CalendarCredential]:
        return self.credential


class FakeSecrets:
    async def resolve(self, credential) -> str:
        return "secret"


class FakeSources:
    def __init__(self, stored: List[CalendarSource] = ()):
        self.stored = list(stored)

    async def list_for(self, credential_id: str) -> List[CalendarSource]:
        return list(self.stored)

    async def replace_all(self, credential_id: str, sources: List[CalendarSource]) -> None:
        self.stored = list(sources)


class FakeAccount:
    """An account with calendars, each holding events; a calendar in [down] can't be read."""

    def __init__(self, calendars: Dict[DiscoveredCalendar, List[CalendarEvent]], down: List[str] = ()):
        self.calendars = calendars
        self.down = set(down)
        self.read: List[str] = []

    async def list_calendars(self, credential, secret) -> List[DiscoveredCalendar]:
        return list(self.calendars)

    async def fetch_events(self, credential, secret, start_time, end_time, limit=50, timeout=10.0, calendar_url=None):
        self.read.append(calendar_url)
        if calendar_url in self.down:
            raise CalendarUnreachableException(f"{calendar_url} did not answer")
        calendar = next(c for c in self.calendars if c.url == calendar_url)
        return [CalendarEvent(**{**e.__dict__, "calendar_name": calendar.name}) for e in self.calendars[calendar]]


def _reader(account: FakeAccount, sources: FakeSources = None) -> GetCalendarEventsUseCase:
    sources = sources or FakeSources()
    return GetCalendarEventsUseCase(
        FakeCredentials(), account, FakeSecrets(), sync=SyncCalendarSourcesUseCase(sources, account)
    )


async def _read(account: FakeAccount, sources: FakeSources = None, limit: int = 50):
    return await _reader(account, sources).read("member-1", MORNING, MORNING + timedelta(days=1), limit=limit)


@pytest.mark.asyncio
async def test_an_appointment_in_a_second_calendar_of_the_account_is_found():
    account = FakeAccount({HOME: [_event("Dinner", 10)], WORK: [_event("Dentist", 2)]})

    reading = await _read(account)

    assert [e.title for e in reading.events] == ["Dentist", "Dinner"]


@pytest.mark.asyncio
async def test_each_event_says_which_calendar_it_is_on_by_key_and_name():
    account = FakeAccount({HOME: [_event("Dinner", 10)], WORK: [_event("Dentist", 2)]})

    reading = await _read(account)

    assert {(e.title, e.calendar_key, e.calendar_name) for e in reading.events} == {
        ("Dentist", "c2", "Work"),
        ("Dinner", "c1", "Home"),
    }


@pytest.mark.asyncio
async def test_a_calendar_the_member_did_not_pick_is_not_read():
    account = FakeAccount({HOME: [_event("Dinner", 10)], HOLIDAYS: [_event("Bank holiday", 0)]})

    reading = await _read(account)

    assert [e.title for e in reading.events] == ["Dinner"]
    assert account.read == [HOME.url]


@pytest.mark.asyncio
async def test_an_event_on_a_read_only_calendar_the_member_picked_says_it_is_read_only():
    picked_holidays = FakeSources([CalendarSource(key="c1", url=HOLIDAYS.url, name="Holidays", writable=False, picked=True)])
    account = FakeAccount({HOLIDAYS: [_event("Bank holiday", 0)]})

    reading = await _read(account, picked_holidays)

    assert reading.events[0].read_only is True


@pytest.mark.asyncio
async def test_one_calendar_out_of_reach_still_gives_the_others_and_names_the_missing_one():
    account = FakeAccount({HOME: [_event("Dinner", 10)], WORK: [_event("Dentist", 2)]}, down=[WORK.url])

    reading = await _read(account)

    assert [e.title for e in reading.events] == ["Dinner"]
    assert reading.unreadable == ["Work"]


@pytest.mark.asyncio
async def test_every_calendar_out_of_reach_is_a_failure():
    account = FakeAccount({HOME: [], WORK: []}, down=[HOME.url, WORK.url])

    with pytest.raises(CalendarIntegrationException):
        await _read(account)


@pytest.mark.asyncio
async def test_the_limit_keeps_the_earliest_events_across_calendars():
    account = FakeAccount({HOME: [_event("Dinner", 10), _event("Lunch", 3)], WORK: [_event("Dentist", 2)]})

    reading = await _read(account, limit=2)

    assert [e.title for e in reading.events] == ["Dentist", "Lunch"]
