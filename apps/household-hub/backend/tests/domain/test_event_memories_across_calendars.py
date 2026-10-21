"""An event memory follows its event on whichever of the member's calendars it is (#252)."""

from datetime import timedelta

import pytest

from app.domain.entities.calendar_source import CalendarSource, DiscoveredCalendar
from app.domain.entities.session import ChatMessage
from app.domain.use_cases.integrations.calendar_sources import SyncCalendarSourcesUseCase
from app.domain.use_cases.memories.event_links import carry_event, event_calendars
from app.domain.use_cases.memories.follow_event import CheckEventMemoriesUseCase, FollowEventChangeUseCase
from tests.domain.reflection_fakes import FakeProposals, FakeUnitOfWork
from tests.domain.test_follow_event import (
    NOW,
    SUN,
    FakeCredentials,
    FakeSecrets,
    TrackingMemories,
    _dinner,
    _event,
)

HOME = DiscoveredCalendar(url="https://cal/home/", name="Home", writable=True)
WORK = DiscoveredCalendar(url="https://cal/work/", name="Work", writable=True)


class FakeSources:
    def __init__(self, stored=()):
        self.stored = list(stored)

    async def list_for(self, credential_id):
        return list(self.stored)

    async def replace_all(self, credential_id, sources):
        self.stored = list(sources)


class CalendarsConnector:
    """Events by (calendar address, event id)."""

    def __init__(self, events, calendars=(HOME, WORK)):
        self.events = events
        self.calendars = list(calendars)
        self.asked = []

    async def list_calendars(self, credential, secret):
        return list(self.calendars)

    async def get_event(self, credential, secret, event_id, occurrence_start=None, timeout=10.0, calendar_url=None):
        self.asked.append((calendar_url, event_id))
        return self.events.get((calendar_url, event_id))


def _follow(memories, connector, sources=None):
    sources = sources or FakeSources()
    return FollowEventChangeUseCase(
        memory_repo=memories, proposal_repo=FakeProposals(), uow=FakeUnitOfWork(),
        credential_repo=FakeCredentials(), connector=connector, secrets=FakeSecrets(), clock=lambda: NOW,
        sync=SyncCalendarSourcesUseCase(sources, connector),
    )


@pytest.mark.asyncio
async def test_a_memory_linked_to_an_event_on_work_is_followed_on_work_only():
    memories = TrackingMemories([_dinner(event_calendar="c2")])
    connector = CalendarsConnector({(WORK.url, "ev1"): _event(start=SUN)})

    await _follow(memories, connector).execute("emma", "ev1", calendar="c2")

    assert connector.asked == [(WORK.url, "ev1")]
    assert memories.memories[0].event_starts_at == SUN


@pytest.mark.asyncio
async def test_a_link_from_before_calendars_could_be_picked_finds_its_calendar_and_keeps_it():
    memories = TrackingMemories([_dinner()])
    connector = CalendarsConnector({(WORK.url, "ev1"): _event()})

    await _follow(memories, connector).execute("emma", "ev1")

    assert memories.memories[0].event_calendar == "c2"
    assert memories.updated == ["dinner"]
    assert memories.memories[0].is_active


@pytest.mark.asyncio
async def test_a_link_from_before_whose_event_is_on_no_picked_calendar_is_retired():
    memories = TrackingMemories([_dinner()])

    await _follow(memories, CalendarsConnector({})).execute("emma", "ev1")

    assert memories.memories[0].is_active is False


@pytest.mark.asyncio
async def test_a_memory_on_a_calendar_the_member_turned_off_is_left_alone():
    sources = FakeSources([
        CalendarSource(key="c1", url=HOME.url, name="Home", writable=True, picked=True),
        CalendarSource(key="c2", url=WORK.url, name="Work", writable=True, picked=False),
    ])
    memories = TrackingMemories([_dinner(event_calendar="c2")])
    connector = CalendarsConnector({})

    await _follow(memories, connector, sources).execute("emma", "ev1", calendar="c2")

    assert connector.asked == []
    assert memories.memories[0].is_active and memories.updated == []


@pytest.mark.asyncio
async def test_the_check_skips_a_turned_off_calendar_and_still_follows_the_others():
    sources = FakeSources([
        CalendarSource(key="c1", url=HOME.url, name="Home", writable=True, picked=True),
        CalendarSource(key="c2", url=WORK.url, name="Work", writable=True, picked=False),
    ])
    memories = TrackingMemories([
        _dinner(id="dinner", event_calendar="c1"),
        _dinner(id="meeting", event_id="ev2", event_calendar="c2"),
    ])
    follow = _follow(memories, CalendarsConnector({}), sources)

    result = await CheckEventMemoriesUseCase(memories, follow, clock=lambda: NOW).run_pass()

    assert {m.id: m.is_active for m in memories.memories} == {"dinner": False, "meeting": True}
    assert result.retired == 1 and result.unreachable == []


def test_the_calendar_of_each_event_a_chat_touched_is_read_from_its_steps():
    steps = [
        {"tool": "calendar_write", "success": True, "data": {"action": "created", "event_id": "ev1", "calendar": "c2"},
         "arguments": {"calendar": "c2"}},
        {"tool": "calendar_write", "success": True, "data": {"action": "updated", "event_id": "ev3"}, "arguments": {}},
    ]
    message = ChatMessage(session_id="s", role="assistant", content="", metadata_json={"tools_executed": steps})

    assert event_calendars([message]) == {"ev1": "c2"}


def test_a_memory_that_takes_over_an_event_link_takes_its_calendar_too():
    new = _dinner(id="new", event_id=None, event_starts_at=None, expires_at=None)
    stored = _dinner(event_calendar="c2", expires_at=NOW + timedelta(days=1))

    carry_event(new, stored, link=True)

    assert (new.event_id, new.event_calendar) == ("ev1", "c2")
