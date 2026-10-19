"""Reflection links a fact to the calendar event it is about (memory spec §7)."""

from datetime import date, datetime, timezone

import pytest

from app.domain.entities.calendar_event import CalendarEvent, Repeat
from app.domain.entities.memory import AgentMemory
from app.domain.exceptions import CalendarUnreachableException
from tests.domain.reflection_fakes import message
from tests.domain.test_reflect_chat import World, _fact

DINNER = CalendarEvent(
    id="ev-dinner",
    title="Dinner together",
    start_time=datetime(2026, 10, 18, 18, 0, tzinfo=timezone.utc),
    end_time=datetime(2026, 10, 18, 20, 0, tzinfo=timezone.utc),
)
YOGA = CalendarEvent(
    id="ev-yoga",
    title="Yoga",
    start_time=datetime(2026, 10, 21, 16, 0, tzinfo=timezone.utc),
    end_time=datetime(2026, 10, 21, 17, 0, tzinfo=timezone.utc),
    repeat=Repeat(frequency="weekly", days=["TU"], until=date(2026, 12, 15)),
)


class FakeEventLookup:
    def __init__(self, events=(), fail=False):
        self.events = {e.id: e for e in events}
        self.fail = fail
        self.asked = []

    async def execute(self, user_id, event_id, occurrence_start=None, timeout=5.0):
        self.asked.append((user_id, event_id))
        if self.fail:
            raise CalendarUnreachableException("down")
        return self.events.get(event_id)


def _step(action, event_id, **arguments):
    return {"tool": "calendar_write", "success": True, "arguments": arguments,
            "data": {"action": action, "event_id": event_id}}


def _chat_with(*steps):
    return [
        message(0, "user", "Add dinner together on Saturday at 20:00", minutes=0, timezone="Europe/Madrid"),
        message(1, "assistant", "Added.", minutes=1, tools_executed=list(steps)),
    ]


def _world(reply, steps, events=(), memories=(), fail=False):
    world = World(reply=reply, messages=_chat_with(*steps), memories=memories)
    world.lookup = FakeEventLookup(events, fail=fail)
    world.use_case.event_lookup = world.lookup
    return world


@pytest.mark.asyncio
async def test_GIVEN_a_fact_about_an_event_the_chat_created_THEN_it_is_saved_with_the_event_its_end_and_its_start():
    world = _world({"facts": [_fact("Dinner together on Saturday 18 October at 20:00", event="e1")]},
                   [_step("created", "ev-dinner")], events=[DINNER])

    await world.run()

    [saved] = world.memories.created
    assert saved.event_id == "ev-dinner"
    assert saved.expires_at == DINNER.end_time
    assert saved.event_starts_at == DINNER.start_time
    assert saved.time_from_event is None


@pytest.mark.asyncio
async def test_the_model_is_shown_the_event_with_its_time_in_the_members_zone():
    world = _world({"facts": []}, [_step("created", "ev-dinner")], events=[DINNER])

    await world.run()

    assert "[e1] Dinner together · Sun 18 Oct 2026, 20:00" in world.llm.prompt
    assert '"event"' in world.llm.calls[-1]["messages"][0].content


@pytest.mark.asyncio
async def test_GIVEN_an_event_created_and_removed_in_the_chat_THEN_it_is_not_offered_or_linked():
    world = _world({"facts": [_fact("Dinner together on Saturday", event="e1")]},
                   [_step("created", "ev-dinner"), _step("deleted", "ev-dinner")], events=[DINNER])

    await world.run()

    assert world.lookup.asked == []
    assert "[e1]" not in world.llm.prompt
    assert [m.event_id for m in world.memories.created] == [None]


@pytest.mark.asyncio
async def test_GIVEN_an_event_the_calendar_no_longer_has_THEN_it_is_not_offered():
    world = _world({"facts": []}, [_step("created", "ev-dinner")], events=[])

    await world.run()

    assert world.lookup.asked == [("emma", "ev-dinner")]
    assert "[e1]" not in world.llm.prompt


@pytest.mark.asyncio
async def test_GIVEN_the_calendar_cant_be_reached_THEN_the_reflection_still_runs_without_links():
    world = _world({"facts": [_fact("Dinner together on Saturday", event="e1")]},
                   [_step("created", "ev-dinner")], events=[DINNER], fail=True)

    await world.run()

    assert [m.event_id for m in world.memories.created] == [None]


@pytest.mark.asyncio
async def test_GIVEN_the_event_is_already_remembered_THEN_no_second_memory_is_added():
    linked = AgentMemory(id="old", user_id="emma", content="Dinner on Saturday", event_id="ev-dinner")
    world = _world({"facts": [_fact("Dinner together on Saturday 18 October", event="e1")]},
                   [_step("updated", "ev-dinner")], events=[DINNER], memories=[linked])

    result = await world.run()

    assert world.memories.created == []
    assert result.same == 1


@pytest.mark.asyncio
async def test_GIVEN_two_facts_about_one_event_THEN_only_the_first_is_linked_and_saved():
    world = _world({"facts": [_fact("Dinner together on Saturday", event="e1"),
                              _fact("Dinner together is at 20:00", event="e1")]},
                   [_step("created", "ev-dinner")], events=[DINNER])

    await world.run()

    assert [m.content for m in world.memories.created] == ["Dinner together on Saturday"]


@pytest.mark.asyncio
async def test_GIVEN_a_repeating_event_THEN_its_memory_expires_when_the_series_ends():
    world = _world({"facts": [_fact("Emma does yoga every Tuesday at 18:00", event="e1")]},
                   [_step("created", "ev-yoga")], events=[YOGA])

    await world.run()

    [saved] = world.memories.created
    assert saved.event_id == "ev-yoga"
    assert saved.expires_at == datetime(2026, 12, 16, tzinfo=timezone.utc)


@pytest.mark.asyncio
async def test_GIVEN_a_repeating_event_without_an_end_THEN_its_memory_never_expires():
    endless = CalendarEvent(**{**YOGA.__dict__, "repeat": Repeat(frequency="weekly", days=["TU"])})
    world = _world({"facts": [_fact("Emma does yoga every Tuesday", event="e1", expires_at="2026-10-21T17:00:00Z")]},
                   [_step("created", "ev-yoga")], events=[endless])

    await world.run()

    assert world.memories.created[0].expires_at is None


@pytest.mark.asyncio
async def test_GIVEN_a_fact_naming_an_event_that_wasnt_offered_THEN_it_is_saved_unlinked():
    world = _world({"facts": [_fact("Dinner on Saturday", event="e7")]}, [_step("created", "ev-dinner")], events=[DINNER])

    await world.run()

    assert world.memories.created[0].event_id is None
