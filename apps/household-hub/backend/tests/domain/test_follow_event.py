"""An event memory follows its event (memory spec §7): through the hub at once, from outside on a schedule."""

import logging
from datetime import date, datetime, timedelta, timezone

import pytest

from app.domain.entities.calendar_event import CalendarEvent, Repeat
from app.domain.entities.integration_credential import CalendarCredential
from app.domain.entities.memory import ALL_DAY, TIMED, AgentMemory
from app.domain.entities.memory_proposal import FROM_REFLECTION, FROM_REMEMBER, MemoryProposal
from app.domain.exceptions import CalendarAuthException, CalendarUnreachableException
from app.domain.use_cases.memories.follow_event import (
    CheckEventMemoriesUseCase,
    FollowEventChangeUseCase,
    match_event,
)
from tests.domain.reflection_fakes import FakeMemories, FakeProposals, FakeUnitOfWork

NOW = datetime(2026, 10, 17, 12, 0, tzinfo=timezone.utc)
SAT = datetime(2026, 10, 18, 18, 0, tzinfo=timezone.utc)
SUN = datetime(2026, 10, 19, 17, 30, tzinfo=timezone.utc)


def _dinner(**kwargs) -> AgentMemory:
    fields = dict(
        id="dinner", user_id="emma", scope="household", content="Dinner together on Saturday 18 October at 20:00",
        event_id="ev1", event_starts_at=SAT, expires_at=SAT + timedelta(hours=2),
    )
    return AgentMemory(**{**fields, **kwargs})


def _event(start=SAT, title="Dinner together", **kwargs) -> CalendarEvent:
    return CalendarEvent(id="ev1", title=title, start_time=start, end_time=start + timedelta(hours=2), **kwargs)


# match_event: what changes on the memory, and the outcome logged.

def test_GIVEN_the_event_is_gone_THEN_the_memory_is_retired():
    memory = _dinner()
    assert match_event(memory, None, NOW) == "retired"
    assert (memory.is_active, memory.retired_at) == (False, NOW)


def test_GIVEN_the_event_moved_THEN_the_sentence_is_its_title_and_the_old_start_is_kept():
    memory = _dinner()
    assert match_event(memory, _event(start=SUN), NOW) == "moved"
    assert memory.content == "Dinner together"
    assert memory.time_from_event == TIMED
    assert (memory.event_starts_at, memory.moved_from) == (SUN, SAT)
    assert memory.expires_at == SUN + timedelta(hours=2)
    assert memory.is_active


def test_GIVEN_an_all_day_event_moved_THEN_its_time_is_shown_as_a_day():
    memory = _dinner()
    day = datetime(2026, 10, 24, tzinfo=timezone.utc)
    match_event(memory, _event(start=day, is_all_day=True), NOW)
    assert memory.time_from_event == ALL_DAY


def test_GIVEN_a_rebuilt_memory_whose_event_was_renamed_THEN_only_the_title_changes():
    memory = _dinner(content="Dinner together", time_from_event=TIMED, moved_from=SAT - timedelta(days=1))
    assert match_event(memory, _event(title="Dinner with Robin"), NOW) == "renamed"
    assert memory.content == "Dinner with Robin"
    assert memory.moved_from == SAT - timedelta(days=1)


def test_GIVEN_nothing_changed_THEN_nothing_is_reported_and_the_models_words_stay():
    memory = _dinner()
    assert match_event(memory, _event(), NOW) is None
    assert memory.content == "Dinner together on Saturday 18 October at 20:00"


def test_GIVEN_a_start_from_SQLite_without_its_zone_THEN_the_same_time_is_not_a_move():
    memory = _dinner(event_starts_at=SAT.replace(tzinfo=None))
    assert match_event(memory, _event(), NOW) is None


def test_GIVEN_a_repeating_series_whose_end_changed_THEN_only_its_expiry_follows():
    yoga = _dinner(content="Emma does yoga every Tuesday", event_starts_at=SAT, expires_at=None)
    series = _event(start=SUN, repeat=Repeat(frequency="weekly", until=date(2026, 11, 30)))
    assert match_event(yoga, series, NOW) == "series_changed"
    assert yoga.expires_at == datetime(2026, 12, 1, tzinfo=timezone.utc)
    assert yoga.content == "Emma does yoga every Tuesday"
    assert (yoga.event_starts_at, yoga.moved_from) == (SAT, None)


def test_GIVEN_a_repeating_series_with_the_same_end_THEN_nothing_changes():
    yoga = _dinner(content="Yoga", expires_at=None)
    assert match_event(yoga, _event(start=SUN, repeat=Repeat(frequency="weekly")), NOW) is None


# The hub's own changes and the periodic check.

class FakeCredentials:
    def __init__(self, members=("emma",), active=True):
        self.members = set(members)
        self.active = active

    async def get_by_user_id(self, user_id):
        if user_id not in self.members:
            return None
        return CalendarCredential(
            id=f"c-{user_id}", user_id=user_id, provider="caldav", url="https://cal", username=user_id,
            encrypted_secret="x", is_active=self.active,
        )


class FakeSecrets:
    def __init__(self, fail_for=()):
        self.fail_for = set(fail_for)

    async def resolve(self, credential):
        if credential.user_id in self.fail_for:
            raise CalendarAuthException("sign in again")
        return "secret"


class FakeConnector:
    def __init__(self, events=None, fail=False):
        self.events = events or {}
        self.fail = fail
        self.asked = []

    async def get_event(self, credential, secret, event_id, occurrence_start=None, timeout=10.0, calendar_url=None):
        self.asked.append((credential.user_id, event_id, occurrence_start))
        if self.fail:
            raise CalendarUnreachableException("down")
        return self.events.get((credential.user_id, event_id))


class TrackingMemories(FakeMemories):
    def __init__(self, memories=()):
        super().__init__(memories)
        self.updated = []

    async def update(self, memory):
        self.updated.append(memory.id)
        return memory

    async def list_linked_current(self, now):
        return [
            m for m in self.memories
            if m.event_id and m.is_active and (m.expires_at is None or m.expires_at > now)
        ]


def _follow(memories, connector, credentials=None, secrets=None, proposals=None):
    uow = FakeUnitOfWork()
    use_case = FollowEventChangeUseCase(
        memory_repo=memories, proposal_repo=proposals or FakeProposals(), uow=uow,
        credential_repo=credentials or FakeCredentials(), connector=connector, secrets=secrets or FakeSecrets(),
        clock=lambda: NOW,
    )
    return use_case, uow


@pytest.mark.asyncio
async def test_GIVEN_the_hub_removed_the_event_THEN_its_memory_is_retired_at_once(caplog):
    memories = TrackingMemories([_dinner()])
    follow, uow = _follow(memories, FakeConnector({}))

    with caplog.at_level(logging.INFO):
        await follow.execute("emma", "ev1")

    assert memories.memories[0].is_active is False
    assert memories.updated == ["dinner"] and uow.commits == 1
    assert "Calendar link | member=emma memory=dinner event=ev1 outcome=retired" in caplog.text


@pytest.mark.asyncio
async def test_GIVEN_the_hub_moved_the_event_THEN_its_memory_is_rebuilt_with_no_change_to_check():
    memories = TrackingMemories([_dinner()])
    proposals = FakeProposals()
    follow, _ = _follow(memories, FakeConnector({("emma", "ev1"): _event(start=SUN)}), proposals=proposals)

    await follow.execute("emma", "ev1")

    assert (memories.memories[0].content, memories.memories[0].moved_from) == ("Dinner together", SAT)
    assert proposals.created == []


@pytest.mark.asyncio
async def test_GIVEN_no_memory_is_linked_THEN_the_calendar_is_not_asked():
    connector = FakeConnector({})
    follow, uow = _follow(TrackingMemories([_dinner(event_id="other")]), connector)

    await follow.execute("emma", "ev1")

    assert connector.asked == [] and uow.commits == 0


@pytest.mark.asyncio
async def test_GIVEN_the_calendar_fails_after_the_change_THEN_the_memory_is_left_for_the_check(caplog):
    memories = TrackingMemories([_dinner()])
    follow, uow = _follow(memories, FakeConnector(fail=True))

    with caplog.at_level(logging.INFO):
        await follow.execute("emma", "ev1")

    assert memories.memories[0].is_active and memories.updated == [] and uow.commits == 0
    assert "Calendar link | member=emma event=ev1 could not be read" in caplog.text


@pytest.mark.asyncio
async def test_GIVEN_a_retired_event_memory_THEN_reflections_changes_on_it_are_dropped_but_a_remember_is_left():
    proposals = FakeProposals([
        MemoryProposal(id="p1", memory_id="dinner", content="x", origin=FROM_REFLECTION, answerer_id="emma"),
        MemoryProposal(id="p2", memory_id="dinner", content="y", origin=FROM_REMEMBER, answerer_id="emma"),
    ])
    follow, _ = _follow(TrackingMemories([_dinner()]), FakeConnector({}), proposals=proposals)

    await follow.execute("emma", "ev1")

    assert [p.state for p in proposals.created] == ["dropped", "pending"]


def _check(memories, connector, credentials=None, secrets=None):
    follow, uow = _follow(memories, connector, credentials, secrets)
    return CheckEventMemoriesUseCase(memory_repo=memories, follow=follow, clock=lambda: NOW), uow


@pytest.mark.asyncio
async def test_the_periodic_check_retires_gone_events_and_rebuilds_moved_ones(caplog):
    memories = TrackingMemories([
        _dinner(),
        _dinner(id="dentist", event_id="ev2", content="Dentist on Friday"),
        _dinner(id="jury", event_id="ev3", content="Jury on Monday"),
    ])
    connector = FakeConnector({("emma", "ev1"): _event(start=SUN), ("emma", "ev3"): CalendarEvent(
        id="ev3", title="Jury", start_time=SAT, end_time=SAT + timedelta(hours=2))})
    check, _ = _check(memories, connector)

    with caplog.at_level(logging.INFO):
        result = await check.run_pass()

    assert (result.checked, result.changed, result.retired) == (3, 1, 1)
    by_id = {m.id: m for m in memories.memories}
    assert by_id["dinner"].moved_from == SAT
    assert by_id["dentist"].is_active is False
    assert by_id["jury"].is_active and by_id["jury"].content == "Jury on Monday"
    assert "Event check | checked=3 changed=1 retired=1 unreachable=" in caplog.text


@pytest.mark.asyncio
async def test_GIVEN_a_calendar_that_cant_be_reached_THEN_the_check_changes_nothing_for_that_member(caplog):
    memories = TrackingMemories([_dinner()])
    check, uow = _check(memories, FakeConnector(fail=True))

    with caplog.at_level(logging.INFO):
        result = await check.run_pass()

    assert memories.memories[0].is_active and memories.updated == [] and uow.commits == 0
    assert result.unreachable == ["emma"]
    assert "unreachable=emma" in caplog.text


@pytest.mark.asyncio
async def test_GIVEN_an_expired_google_sign_in_THEN_nothing_is_retired_until_the_member_reconnects():
    memories = TrackingMemories([_dinner()])
    connector = FakeConnector({})
    check, _ = _check(memories, connector, secrets=FakeSecrets(fail_for={"emma"}))

    result = await check.run_pass()

    assert memories.memories[0].is_active and connector.asked == []
    assert result.unreachable == ["emma"]


@pytest.mark.asyncio
async def test_GIVEN_a_member_who_disconnected_their_calendar_THEN_nothing_is_retired():
    memories = TrackingMemories([_dinner()])
    connector = FakeConnector({})
    check, _ = _check(memories, connector, credentials=FakeCredentials(members=()))

    result = await check.run_pass()

    assert memories.memories[0].is_active and connector.asked == []
    assert result.unreachable == ["emma"]


@pytest.mark.asyncio
async def test_the_check_asks_each_members_own_calendar_and_skips_expired_memories():
    memories = TrackingMemories([
        _dinner(),
        _dinner(id="liams", user_id="liam", event_id="ev9"),
        _dinner(id="past", event_id="ev0", expires_at=NOW - timedelta(hours=1)),
    ])
    connector = FakeConnector({("emma", "ev1"): _event(), ("liam", "ev9"): _event()})
    check, _ = _check(memories, connector, credentials=FakeCredentials(members=("emma", "liam")))

    result = await check.run_pass()

    assert sorted((user, event) for user, event, _ in connector.asked) == [("emma", "ev1"), ("liam", "ev9")]
    assert (result.checked, result.changed, result.retired) == (2, 0, 0)
