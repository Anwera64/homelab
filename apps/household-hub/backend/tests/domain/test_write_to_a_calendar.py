from datetime import datetime, timedelta, timezone
from typing import List

import pytest

from app.domain.entities.calendar_event import CalendarEvent
from app.domain.entities.calendar_source import CalendarSource
from app.domain.exceptions import CalendarIntegrationException
from app.domain.use_cases.integrations.calendar_sources import SyncCalendarSourcesUseCase
from app.domain.use_cases.integrations.create_calendar_event import CreateCalendarEventUseCase
from app.domain.use_cases.integrations.delete_calendar_event import DeleteCalendarEventUseCase
from app.domain.use_cases.integrations.get_calendar_event import GetCalendarEventUseCase
from app.domain.use_cases.integrations.update_calendar_event import UpdateCalendarEventUseCase
from tests.domain.test_read_every_calendar import HOLIDAYS, HOME, WORK, FakeCredentials, FakeSecrets, FakeSources

START = datetime(2026, 10, 24, 19, tzinfo=timezone.utc)


class FakeWritableAccount:
    """Records which calendar each write went to."""

    def __init__(self, calendars):
        self.calendars = calendars
        self.wrote_to: List[str] = []

    async def list_calendars(self, credential, secret):
        return list(self.calendars)

    def _event(self, calendar_url, event_id="dinner-uid"):
        self.wrote_to.append(calendar_url)
        return CalendarEvent(id=event_id, title="Dinner", start_time=START, end_time=START + timedelta(hours=2))

    async def create_event(self, credential, secret, title, start_time, end_time, description="", location="",
                           is_all_day=False, timeout=10.0, repeat=None, calendar_url=None):
        return self._event(calendar_url)

    async def update_event(self, credential, secret, event_id, calendar_url=None, **changes):
        return self._event(calendar_url, event_id)

    async def delete_event(self, credential, secret, event_id, timeout=10.0, occurrence_start=None, scope=None,
                           calendar_url=None):
        self._event(calendar_url, event_id)
        return True

    async def get_event(self, credential, secret, event_id, occurrence_start=None, timeout=10.0, calendar_url=None):
        return self._event(calendar_url, event_id)


def _deps(account, sources=None):
    sources = sources or FakeSources()
    return FakeCredentials(), account, FakeSecrets(), SyncCalendarSourcesUseCase(sources, account)


async def _create(account, sources=None, calendar=None):
    creds, conn, secrets, sync = _deps(account, sources)
    return await CreateCalendarEventUseCase(creds, conn, secrets, sync=sync).execute(
        "member-1", "Dinner", START, START + timedelta(hours=2), calendar=calendar
    )


@pytest.mark.asyncio
async def test_with_one_calendar_to_write_to_a_new_event_goes_there_without_asking():
    account = FakeWritableAccount([HOME, HOLIDAYS])

    created = await _create(account)

    assert account.wrote_to == [HOME.url]
    assert created.calendar_key == "c1"


@pytest.mark.asyncio
async def test_with_two_calendars_to_write_to_a_new_event_without_a_calendar_asks_which():
    account = FakeWritableAccount([HOME, WORK])

    with pytest.raises(CalendarIntegrationException) as asked:
        await _create(account)

    assert "Home (c1)" in asked.value.message and "Work (c2)" in asked.value.message
    assert "ask the member" in asked.value.message.lower()
    assert account.wrote_to == []


@pytest.mark.asyncio
async def test_a_new_event_goes_to_the_calendar_named():
    account = FakeWritableAccount([HOME, WORK])

    created = await _create(account, calendar="c2")

    assert account.wrote_to == [WORK.url]
    assert (created.calendar_key, created.calendar_name) == ("c2", "Work")


@pytest.mark.asyncio
async def test_a_read_only_calendar_is_refused_for_a_new_event():
    sources = FakeSources([
        CalendarSource(key="c1", url=HOME.url, name="Home", writable=True, picked=True),
        CalendarSource(key="c2", url=HOLIDAYS.url, name="Holidays", writable=False, picked=True),
    ])
    account = FakeWritableAccount([HOME, HOLIDAYS])

    with pytest.raises(CalendarIntegrationException) as refused:
        await _create(account, sources, calendar="c2")

    assert "read-only" in refused.value.message
    assert account.wrote_to == []


@pytest.mark.asyncio
async def test_a_calendar_key_the_member_does_not_have_is_refused_with_the_ones_they_do():
    account = FakeWritableAccount([HOME, WORK])

    with pytest.raises(CalendarIntegrationException) as refused:
        await _create(account, calendar="c9")

    assert "c9" in refused.value.message and "Home (c1)" in refused.value.message


@pytest.mark.asyncio
async def test_a_change_goes_to_the_calendar_the_event_is_on():
    account = FakeWritableAccount([HOME, WORK])
    creds, conn, secrets, sync = _deps(account)

    updated = await UpdateCalendarEventUseCase(creds, conn, secrets, sync=sync).execute(
        "member-1", "dinner-uid", title="Dinner out", calendar="c2"
    )

    assert account.wrote_to == [WORK.url]
    assert updated.calendar_key == "c2"


@pytest.mark.asyncio
async def test_a_change_without_a_calendar_with_two_picked_says_to_pass_it_as_calendar_read_gave_it():
    account = FakeWritableAccount([HOME, WORK])
    creds, conn, secrets, sync = _deps(account)

    with pytest.raises(CalendarIntegrationException) as refused:
        await UpdateCalendarEventUseCase(creds, conn, secrets, sync=sync).execute("member-1", "dinner-uid", title="x")

    assert "calendar_read" in refused.value.message
    assert account.wrote_to == []


@pytest.mark.asyncio
async def test_a_removal_on_a_read_only_calendar_is_refused():
    sources = FakeSources([CalendarSource(key="c1", url=HOLIDAYS.url, name="Holidays", writable=False, picked=True)])
    account = FakeWritableAccount([HOLIDAYS])
    creds, conn, secrets, sync = _deps(account, sources)

    with pytest.raises(CalendarIntegrationException):
        await DeleteCalendarEventUseCase(creds, conn, secrets, sync=sync).execute("member-1", "dinner-uid", calendar="c1")

    assert account.wrote_to == []


@pytest.mark.asyncio
async def test_a_removal_with_one_calendar_picked_needs_no_calendar():
    account = FakeWritableAccount([HOME])
    creds, conn, secrets, sync = _deps(account)

    await DeleteCalendarEventUseCase(creds, conn, secrets, sync=sync).execute("member-1", "dinner-uid")

    assert account.wrote_to == [HOME.url]


@pytest.mark.asyncio
async def test_looking_up_an_event_on_a_read_only_calendar_is_allowed():
    sources = FakeSources([
        CalendarSource(key="c1", url=HOME.url, name="Home", writable=True, picked=True),
        CalendarSource(key="c2", url=HOLIDAYS.url, name="Holidays", writable=False, picked=True),
    ])
    account = FakeWritableAccount([HOME, HOLIDAYS])
    creds, conn, secrets, sync = _deps(account, sources)

    event = await GetCalendarEventUseCase(creds, conn, secrets, sync=sync).execute("member-1", "x", calendar="c2")

    assert account.wrote_to == [HOLIDAYS.url]
    assert (event.calendar_key, event.read_only) == ("c2", True)
