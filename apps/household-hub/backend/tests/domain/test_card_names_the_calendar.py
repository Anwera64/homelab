"""An event card says which of the member's calendars it is about (#252)."""
from datetime import datetime, timedelta, timezone

import pytest

from app.domain.entities.calendar_event import CalendarEvent
from app.domain.exceptions import CalendarChoiceException
from app.domain.entities.llm_message import LLMResponseChunk, LLMToolCall
from app.domain.use_cases.chat.tool_approval import event_on_card
from tests.domain.test_tool_approval import DINNER, _ask, _setup

DENTIST = CalendarEvent(
    id="e1",
    title="Dentist",
    start_time=datetime(2026, 10, 22, 9, 30, tzinfo=timezone.utc),
    end_time=datetime(2026, 10, 22, 10, 30, tzinfo=timezone.utc),
    calendar_key="c2",
    calendar_name="Work",
)


def test_GIVEN_an_event_on_work_WHEN_its_removal_is_carded_THEN_the_card_names_work():
    shown = event_on_card("delete", {"action": "delete", "event_id": "e1", "calendar": "c2"}, DENTIST, None)

    assert shown["calendar_name"] == "Work"


def test_GIVEN_an_event_on_work_WHEN_a_change_to_it_is_carded_THEN_the_card_names_work():
    shown = event_on_card("update", {"action": "update", "event_id": "e1", "title": "Dentist (moved)"}, DENTIST, None)

    assert shown["calendar_name"] == "Work"


def test_GIVEN_a_connection_from_before_calendars_were_kept_WHEN_carded_THEN_no_calendar_is_named():
    from dataclasses import replace

    shown = event_on_card("delete", {"action": "delete", "event_id": "e1"}, replace(DENTIST, calendar_key=""), None)

    assert "calendar_name" not in shown


class FakeCalendarForCard:
    def __init__(self, name=None, ask=None):
        self.name = name
        self.ask = ask
        self.asked = []

    async def execute(self, user_id, key):
        self.asked.append((user_id, key))
        if self.ask:
            raise CalendarChoiceException(self.ask)
        return self.name


@pytest.mark.asyncio
async def test_GIVEN_a_new_event_WHEN_its_card_is_shown_THEN_it_names_the_calendar_it_goes_to():
    family = FakeCalendarForCard(name="Family")
    dinner_on_family = LLMToolCall(id=DINNER.id, name=DINNER.name, arguments={**DINNER.arguments, "calendar": "c3"})
    use_case, _, _, _ = _setup([[LLMResponseChunk(tool_calls=[dinner_on_family])]], calendar_for_card=family)

    events = await _ask(use_case)

    [proposal] = [ev for ev in events if ev["type"] == "tool_approval_proposal"]
    assert proposal["arguments"]["calendar_name"] == "Family"
    assert family.asked == [("u1", "c3")]


@pytest.mark.asyncio
async def test_GIVEN_two_calendars_to_add_to_WHEN_the_model_names_none_THEN_no_card_is_shown_and_it_is_told_to_ask():
    asks = FakeCalendarForCard(ask="The member has more than one calendar to add it to: Home (c1), Work (c2). Ask the member which one.")
    use_case, _, llm_client, tool_executor = _setup([[LLMResponseChunk(tool_calls=[DINNER])], []], calendar_for_card=asks)

    events = await _ask(use_case)

    assert not any(ev["type"] == "tool_approval_proposal" for ev in events)
    assert tool_executor.executed_calls == []
    told = str(llm_client.stream_calls[-1])
    assert "Ask the member which one" in told
