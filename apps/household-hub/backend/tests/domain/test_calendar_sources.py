from typing import Dict, List

import pytest

from app.domain.entities.calendar_source import CalendarSource, DiscoveredCalendar
from app.domain.entities.integration_credential import CalendarCredential
from app.domain.use_cases.integrations.calendar_sources import SyncCalendarSourcesUseCase


HOME = DiscoveredCalendar(url="https://cal.example/home/", name="Home", writable=True)
WORK = DiscoveredCalendar(url="https://cal.example/work/", name="Work", writable=True)
HOLIDAYS = DiscoveredCalendar(url="https://cal.example/holidays/", name="Holidays", writable=False)


class FakeSourceRepository:
    def __init__(self, stored: List[CalendarSource] = ()):
        self.by_credential: Dict[str, List[CalendarSource]] = {"cred-1": list(stored)}

    async def list_for(self, credential_id: str) -> List[CalendarSource]:
        return list(self.by_credential.get(credential_id, []))

    async def replace_all(self, credential_id: str, sources: List[CalendarSource]) -> None:
        self.by_credential[credential_id] = list(sources)


class FakeConnector:
    def __init__(self, calendars: List[DiscoveredCalendar]):
        self.calendars = calendars

    async def list_calendars(self, credential, secret) -> List[DiscoveredCalendar]:
        return list(self.calendars)


def _credential() -> CalendarCredential:
    return CalendarCredential(
        id="cred-1", user_id="member-1", provider="caldav", url="https://cal.example/", username="m", encrypted_secret="x"
    )


async def _sync(stored: List[CalendarSource], account: List[DiscoveredCalendar]) -> List[CalendarSource]:
    repo = FakeSourceRepository(stored)
    synced = await SyncCalendarSourcesUseCase(repo, FakeConnector(account)).execute(_credential(), "secret")
    assert synced == await repo.list_for("cred-1"), "what is returned is what is saved"
    return synced


def _by_name(sources: List[CalendarSource]) -> Dict[str, CalendarSource]:
    return {s.name: s for s in sources}


@pytest.mark.asyncio
async def test_a_calendar_the_member_can_write_to_is_read_from_the_start():
    synced = await _sync([], [HOME, WORK])
    assert all(s.picked for s in synced)


@pytest.mark.asyncio
async def test_a_read_only_calendar_is_not_read_until_the_member_picks_it():
    synced = _by_name(await _sync([], [HOME, HOLIDAYS]))
    assert synced["Holidays"].picked is False
    assert synced["Holidays"].writable is False


@pytest.mark.asyncio
async def test_a_calendar_the_member_turned_off_stays_off():
    stored = [CalendarSource(key="c1", url=HOME.url, name="Home", writable=True, picked=False)]
    synced = _by_name(await _sync(stored, [HOME]))
    assert synced["Home"].picked is False


@pytest.mark.asyncio
async def test_a_read_only_calendar_the_member_picked_stays_picked():
    stored = [CalendarSource(key="c1", url=HOLIDAYS.url, name="Holidays", writable=False, picked=True)]
    synced = _by_name(await _sync(stored, [HOLIDAYS]))
    assert synced["Holidays"].picked is True


@pytest.mark.asyncio
async def test_a_calendar_added_to_the_account_later_gets_the_same_default():
    stored = [CalendarSource(key="c1", url=HOME.url, name="Home", writable=True, picked=True)]
    synced = _by_name(await _sync(stored, [HOME, WORK, HOLIDAYS]))
    assert synced["Work"].picked is True
    assert synced["Holidays"].picked is False


@pytest.mark.asyncio
async def test_a_calendar_removed_from_the_account_is_dropped():
    stored = [
        CalendarSource(key="c1", url=HOME.url, name="Home", writable=True, picked=True),
        CalendarSource(key="c2", url=WORK.url, name="Work", writable=True, picked=True),
    ]
    synced = await _sync(stored, [HOME])
    assert [s.name for s in synced] == ["Home"]


@pytest.mark.asyncio
async def test_a_calendar_keeps_its_key_and_a_new_one_never_takes_a_used_key():
    stored = [
        CalendarSource(key="c1", url=HOME.url, name="Home", writable=True, picked=True),
        CalendarSource(key="c3", url=WORK.url, name="Work", writable=True, picked=True),
    ]
    synced = _by_name(await _sync(stored, [WORK, HOME, HOLIDAYS]))
    assert synced["Home"].key == "c1"
    assert synced["Work"].key == "c3"
    assert synced["Holidays"].key == "c4"


@pytest.mark.asyncio
async def test_a_renamed_calendar_keeps_its_key_and_shows_its_new_name():
    stored = [CalendarSource(key="c1", url=HOME.url, name="Home", writable=True, picked=False)]
    renamed = DiscoveredCalendar(url=HOME.url, name="Family", writable=True)
    synced = await _sync(stored, [renamed])
    assert (synced[0].key, synced[0].name, synced[0].picked) == ("c1", "Family", False)
