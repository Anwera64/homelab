"""
Approving or declining a write (slice 4, PR 3).

A write that needs asking pauses the turn: the round's calls are saved with the answer, the phone is
sent a proposal per write, and nothing more happens until the member decides. After the last
decision the approved calls run, the declined ones tell the model so, and the same answer carries on
from what was saved. The hub keeps nothing in memory while it waits.
"""
import json
from datetime import datetime, timezone
from zoneinfo import ZoneInfo

import pytest

from app.domain.entities.calendar_event import CalendarEvent, Repeat
from app.domain.entities.agent import AgentPersonality
from app.domain.entities.llm_message import LLMResponseChunk, LLMToolCall
from app.domain.entities.session import ChatMessage, ConversationSession
from app.domain.entities.tool_definition import ToolDefinition
from app.domain.entities.user import User
from app.domain.exceptions import CalendarUnreachableException, EntityNotFoundException, InvalidOperationException
from app.domain.use_cases.chat.process_chat_turn import ProcessChatTurnUseCase
from app.domain.use_cases.chat.tool_approval import (
    MEMBER_EDITED,
    PAUSED_TURN,
    DropPendingProposalsUseCase,
    is_awaiting_approval,
)
from tests.domain.test_chat_turn import (
    FakeAgentRepository,
    FakeContextAssembler,
    FakeLLMClient,
    FakeSessionRepository,
    FakeToolExecutor,
    FakeUnitOfWork,
    _house_resolver,
)


class SavingSessionRepository(FakeSessionRepository):
    """Keeps messages the way the database does: one row per id, updated in place."""

    async def update_message(self, message: ChatMessage) -> ChatMessage:
        for index, saved in enumerate(self.messages):
            if saved.id == message.id:
                self.messages[index] = message
                return message
        raise AssertionError("updated a message that was never saved")

    async def get_messages(self, session_id: str, limit: int = 50, before_id=None):
        return [m for m in self.messages if m.session_id == session_id][-limit:]

    async def list_ids_by_agent_id(self, agent_id: str):
        return [s.id for s in self.sessions.values() if s.agent_id == agent_id]


class WriteToolLister:
    async def execute(self):
        return [
            ToolDefinition(name="calendar_read", description="Read calendar", parameters_schema={"type": "object"}),
            ToolDefinition(name="calendar_write", description="Write calendar", parameters_schema={"type": "object"}),
            ToolDefinition(name="document_writer", description="Write notes", parameters_schema={"type": "object"}),
        ]


USER = User(id="u1", full_name="Alex")
DINNER = LLMToolCall(
    id="c1",
    name="calendar_write",
    arguments={"action": "create", "title": "Dinner together", "start_time": "2026-10-03T20:30:00"},
)
FLOWERS = LLMToolCall(
    id="c2",
    name="calendar_write",
    arguments={"action": "create", "title": "Buy flowers", "start_time": "2026-10-02T10:00:00"},
)
CHECK = LLMToolCall(id="r1", name="calendar_read", arguments={"date": "2026-10-03"})


def _agent(**overrides):
    fields = dict(id="a1", name="Assistant", tool_permissions=["calendar_read", "calendar_write", "document_writer"])
    fields.update(overrides)
    return AgentPersonality(**fields)


def _setup(rounds, agent=None, is_secret=False, approvals=None, event_lookup=None):
    agent = agent or _agent()
    session_repo = SavingSessionRepository(
        sessions=[ConversationSession(id="s1", user_id="u1", agent_id="a1", is_secret=is_secret)]
    )
    llm_client = FakeLLMClient(stream_chunks_list=rounds)
    tool_executor = FakeToolExecutor()
    use_case = ProcessChatTurnUseCase(
        session_repo=session_repo,
        agent_repo=FakeAgentRepository(agents=[agent]),
        llm_client=llm_client,
        context_assembler=FakeContextAssembler(),
        tool_executor=tool_executor,
        tool_lister=WriteToolLister(),
        model_resolver=_house_resolver(),
        uow=FakeUnitOfWork(),
        approval_repo=approvals,
        event_lookup=event_lookup,
    )
    return use_case, session_repo, llm_client, tool_executor


async def _ask(use_case, content="Put dinner in the calendar for Saturday"):
    return [ev async for ev in use_case.execute_stream(session_id="s1", current_user=USER, content=content)]


async def _decide(use_case, tool_call_id, approved, modified_arguments=None):
    return [
        ev
        async for ev in use_case.decide_stream(
            session_id="s1",
            current_user=USER,
            tool_call_id=tool_call_id,
            approved=approved,
            modified_arguments=modified_arguments,
        )
    ]


def _answers(session_repo):
    return [m for m in session_repo.messages if m.role == "assistant"]


@pytest.mark.asyncio
async def test_GIVEN_a_write_WHEN_the_model_asks_for_it_THEN_the_turn_pauses_on_a_proposal():
    use_case, session_repo, llm_client, tool_executor = _setup(
        [[LLMResponseChunk(delta_content="I can add it now."), LLMResponseChunk(tool_calls=[DINNER])]]
    )

    events = await _ask(use_case)

    proposals = [ev for ev in events if ev["type"] == "tool_approval_proposal"]
    assert proposals == [
        {
            "type": "tool_approval_proposal",
            "tool_call_id": "c1",
            "tool": "calendar_write",
            "action": "create",
            "arguments": DINNER.arguments,
        }
    ]
    assert tool_executor.executed_calls == []
    # Paused, not answered: the model is not asked to say anything more until the member decides.
    assert len(llm_client.stream_calls) == 1
    assert events[-1]["type"] == "awaiting_approval"
    assert not any(ev["type"] == "done" for ev in events)

    [answer] = _answers(session_repo)
    assert events[-1]["message_id"] == answer.id
    assert answer.content == "I can add it now."
    assert answer.metadata_json["parts"] == [
        {"type": "text", "content": "I can add it now."},
        {
            "type": "proposal",
            "tool_call_id": "c1",
            "tool": "calendar_write",
            "action": "create",
            "arguments": DINNER.arguments,
            "status": "pending",
        },
    ]
    assert PAUSED_TURN in answer.metadata_json
    assert is_awaiting_approval(session_repo.messages)


@pytest.mark.asyncio
async def test_GIVEN_a_read_and_a_write_in_one_round_WHEN_paused_THEN_the_read_has_already_run():
    use_case, session_repo, _, tool_executor = _setup([[LLMResponseChunk(tool_calls=[DINNER, CHECK])]])

    events = await _ask(use_case)

    assert [call["tool"] for call in tool_executor.executed_calls] == ["calendar_read"]
    types = [ev["type"] for ev in events]
    assert types.index("tool_result") < types.index("tool_approval_proposal")
    [answer] = _answers(session_repo)
    assert [part["type"] for part in answer.metadata_json["parts"]] == ["tool", "proposal"]


@pytest.mark.asyncio
async def test_GIVEN_a_paused_turn_WHEN_approved_THEN_the_write_runs_and_the_same_answer_carries_on():
    use_case, session_repo, llm_client, tool_executor = _setup(
        [
            [LLMResponseChunk(delta_content="I can add it now."), LLMResponseChunk(tool_calls=[DINNER])],
            [LLMResponseChunk(delta_content="Done. Anything else?")],
        ]
    )
    await _ask(use_case)

    events = await _decide(use_case, "c1", approved=True)

    assert events[0] == {"type": "accepted"}
    assert [call["tool"] for call in tool_executor.executed_calls] == ["calendar_write"]
    assert tool_executor.executed_calls[0]["args"] == DINNER.arguments
    types = [ev["type"] for ev in events]
    assert types.index("tool_executing") < types.index("tool_result") < types.index("delta")

    # The model reads the write's result against the call it made, after everything it saw before.
    resumed = llm_client.stream_calls[1]["messages"]
    assert [m.role for m in resumed[-2:]] == ["assistant", "tool"]
    assert resumed[-2].tool_calls[0].id == "c1"
    assert resumed[-1].tool_call_id == "c1"
    assert json.loads(resumed[-1].content) == {"result": "ok"}

    # One answer, not two: the paused one is finished in place.
    [answer] = _answers(session_repo)
    assert events[-1]["type"] == "done"
    assert events[-1]["message_id"] == answer.id
    assert answer.content == "I can add it now.\n\nDone. Anything else?"
    assert [part["type"] for part in answer.metadata_json["parts"]] == ["text", "tool", "text"]
    assert answer.metadata_json["parts"][1]["summary"] == {"action": "create", "title": "Dinner together"}
    assert PAUSED_TURN not in answer.metadata_json
    assert not is_awaiting_approval(session_repo.messages)


@pytest.mark.asyncio
async def test_GIVEN_a_paused_turn_WHEN_declined_THEN_nothing_runs_and_the_model_is_told():
    use_case, session_repo, llm_client, tool_executor = _setup(
        [
            [LLMResponseChunk(tool_calls=[FLOWERS])],
            [LLMResponseChunk(delta_content="No problem, I'll leave that one off.")],
        ]
    )
    await _ask(use_case, "Add a reminder to buy flowers on Friday")

    events = await _decide(use_case, "c2", approved=False)

    assert tool_executor.executed_calls == []
    told = llm_client.stream_calls[1]["messages"][-1]
    assert told.role == "tool" and told.tool_call_id == "c2"
    assert "declined" in told.content
    assert {"type": "tool_declined", "tool": "calendar_write", "summary": {"action": "create", "title": "Buy flowers"}} in events
    [answer] = _answers(session_repo)
    assert answer.metadata_json["parts"][0] == {
        "type": "declined",
        "tool": "calendar_write",
        "summary": {"action": "create", "title": "Buy flowers"},
    }


@pytest.mark.asyncio
async def test_GIVEN_two_writes_WHEN_the_first_is_decided_THEN_the_turn_waits_for_the_second():
    use_case, session_repo, llm_client, tool_executor = _setup(
        [
            [LLMResponseChunk(tool_calls=[DINNER, FLOWERS])],
            [LLMResponseChunk(delta_content="Added dinner, left the flowers off.")],
        ]
    )
    events = await _ask(use_case)
    assert [ev["tool_call_id"] for ev in events if ev["type"] == "tool_approval_proposal"] == ["c1", "c2"]

    first = await _decide(use_case, "c1", approved=True)

    assert tool_executor.executed_calls == []
    assert len(llm_client.stream_calls) == 1
    assert first[-1]["type"] == "awaiting_approval"
    statuses = [part.get("status") for part in _answers(session_repo)[0].metadata_json["parts"]]
    assert statuses == ["approved", "pending"]

    second = await _decide(use_case, "c2", approved=False)

    assert [call["args"]["title"] for call in tool_executor.executed_calls] == ["Dinner together"]
    assert second[-1]["type"] == "done"
    parts = _answers(session_repo)[0].metadata_json["parts"]
    assert [part["type"] for part in parts] == ["tool", "declined", "text"]
    # Each call gets its own answer, in the order the model made them.
    resumed = llm_client.stream_calls[1]["messages"]
    assert [m.tool_call_id for m in resumed if m.role == "tool"] == ["c1", "c2"]


@pytest.mark.asyncio
async def test_GIVEN_changed_details_WHEN_approved_THEN_the_write_runs_with_them():
    use_case, _, _, tool_executor = _setup(
        [[LLMResponseChunk(tool_calls=[DINNER])], [LLMResponseChunk(delta_content="Done.")]]
    )
    await _ask(use_case)

    await _decide(use_case, "c1", approved=True, modified_arguments={"start_time": "2026-10-03T21:00:00"})

    assert tool_executor.executed_calls[0]["args"] == {**DINNER.arguments, "start_time": "2026-10-03T21:00:00"}


@pytest.mark.asyncio
async def test_GIVEN_changed_details_WHEN_approved_THEN_the_model_is_told_what_the_member_changed():
    use_case, _, llm_client, _ = _setup(
        [[LLMResponseChunk(tool_calls=[DINNER])], [LLMResponseChunk(delta_content="Done.")]]
    )
    await _ask(use_case)

    # The phone sends back details it did not change too; only the start really differs.
    await _decide(
        use_case, "c1", approved=True, modified_arguments={"start_time": "2026-10-03T21:00:00", "action": "create"}
    )

    resumed = llm_client.stream_calls[1]["messages"]
    # The call the model reads is the call that ran.
    assert resumed[-2].tool_calls[0].arguments == {**DINNER.arguments, "start_time": "2026-10-03T21:00:00"}
    told = json.loads(resumed[-1].content)
    assert told["result"] == "ok"
    assert told["member_edited"] == {"changed": {"start_time": "2026-10-03T21:00:00"}, "note": MEMBER_EDITED}


@pytest.mark.asyncio
async def test_GIVEN_two_writes_WHEN_the_first_is_approved_with_changes_THEN_the_model_is_told_after_the_last_decision():
    use_case, _, llm_client, _ = _setup(
        [[LLMResponseChunk(tool_calls=[DINNER, FLOWERS])], [LLMResponseChunk(delta_content="Both added.")]]
    )
    await _ask(use_case)

    await _decide(use_case, "c1", approved=True, modified_arguments={"title": "Dinner at Nonna's"})
    await _decide(use_case, "c2", approved=True)

    resumed = llm_client.stream_calls[1]["messages"]
    results = {m.tool_call_id: json.loads(m.content) for m in resumed if m.role == "tool"}
    assert results["c1"]["member_edited"]["changed"] == {"title": "Dinner at Nonna's"}
    assert results["c2"] == {"result": "ok"}


@pytest.mark.asyncio
async def test_GIVEN_no_such_proposal_WHEN_decided_THEN_it_is_not_found():
    use_case, _, _, _ = _setup([[LLMResponseChunk(tool_calls=[DINNER])]])
    await _ask(use_case)

    with pytest.raises(EntityNotFoundException):
        await _decide(use_case, "nope", approved=True)


@pytest.mark.asyncio
async def test_GIVEN_a_decided_proposal_WHEN_decided_again_THEN_it_is_refused():
    use_case, _, _, _ = _setup([[LLMResponseChunk(tool_calls=[DINNER, FLOWERS])]])
    await _ask(use_case)
    await _decide(use_case, "c1", approved=True)

    with pytest.raises(InvalidOperationException):
        await _decide(use_case, "c1", approved=False)


@pytest.mark.asyncio
async def test_GIVEN_a_secret_turn_WHEN_the_model_asks_for_a_write_THEN_no_card_is_shown():
    """Writes are refused in secret mode anyway, so asking the member would only ask for nothing."""
    use_case, session_repo, _, tool_executor = _setup(
        [[LLMResponseChunk(tool_calls=[DINNER])], [LLMResponseChunk(delta_content="I can't in a secret chat.")]],
        is_secret=True,
    )

    events = await _ask(use_case)

    assert not any(ev["type"] == "tool_approval_proposal" for ev in events)
    assert events[-1]["type"] == "done"
    assert not is_awaiting_approval(session_repo.messages)


@pytest.mark.asyncio
async def test_GIVEN_a_waiting_card_WHEN_its_agent_is_dropped_THEN_the_card_goes_and_the_answer_stays():
    use_case, session_repo, _, _ = _setup(
        [[LLMResponseChunk(delta_content="I can add it now."), LLMResponseChunk(tool_calls=[DINNER])]]
    )
    await _ask(use_case)

    await DropPendingProposalsUseCase(session_repo, FakeUnitOfWork()).for_agent("a1")

    [answer] = _answers(session_repo)
    assert answer.metadata_json["parts"] == [{"type": "text", "content": "I can add it now."}]
    assert PAUSED_TURN not in answer.metadata_json
    assert not is_awaiting_approval(session_repo.messages)


# A remove or change card names the event it is about, even when the model only sent its ID (#63).
REMOVE = LLMToolCall(id="d1", name="calendar_write", arguments={"action": "delete", "event_id": "e9f57d50"})
MOVE = LLMToolCall(
    id="u1",
    name="calendar_write",
    arguments={"action": "update", "event_id": "e9f57d50", "start_time": "2026-10-01T10:00:00+02:00"},
)


class FakeEventLookup:
    def __init__(self, events=(), fails=False):
        self.events = {event.id: event for event in events}
        self.fails = fails
        self.asked = []
        self.dates = []

    async def execute(self, user_id, event_id, occurrence_start=None, timeout=5.0):
        self.asked.append((user_id, event_id))
        self.dates.append(occurrence_start)
        if self.fails:
            raise CalendarUnreachableException("calendar.example.com did not answer")
        return self.events.get(event_id)


def _test_event():
    return CalendarEvent(
        id="e9f57d50",
        title="Test Event",
        start_time=datetime(2026, 9, 30, 9, 0, tzinfo=timezone.utc),
        end_time=datetime(2026, 9, 30, 9, 30, tzinfo=timezone.utc),
    )


async def _ask_from(use_case, timezone_name):
    return [
        ev
        async for ev in use_case.execute_stream(
            session_id="s1", current_user=USER, content="Remove the test event", timezone_name=timezone_name
        )
    ]


@pytest.mark.asyncio
async def test_GIVEN_a_remove_with_only_an_id_WHEN_its_card_is_shown_THEN_it_names_the_event_and_its_time():
    lookup = FakeEventLookup([_test_event()])
    use_case, session_repo, _, _ = _setup([[LLMResponseChunk(tool_calls=[REMOVE])]], event_lookup=lookup)

    events = await _ask_from(use_case, "Europe/Madrid")

    [proposal] = [ev for ev in events if ev["type"] == "tool_approval_proposal"]
    assert proposal["arguments"] == {
        "action": "delete",
        "event_id": "e9f57d50",
        "title": "Test Event",
        "start_time": "2026-09-30T11:00:00+02:00",
        "end_time": "2026-09-30T11:30:00+02:00",
        "is_all_day": False,
    }
    assert lookup.asked == [("u1", "e9f57d50")]
    # Saved with the card, so it still names the event when the chat is reopened.
    [answer] = _answers(session_repo)
    [card] = [part for part in answer.metadata_json["parts"] if part["type"] == "proposal"]
    assert card["arguments"] == proposal["arguments"]
    # The model's own call is left as it made it.
    assert REMOVE.arguments == {"action": "delete", "event_id": "e9f57d50"}


@pytest.mark.asyncio
async def test_GIVEN_a_change_with_no_title_WHEN_its_card_is_shown_THEN_it_names_the_event_and_keeps_the_new_time():
    use_case, _, _, _ = _setup([[LLMResponseChunk(tool_calls=[MOVE])]], event_lookup=FakeEventLookup([_test_event()]))

    events = await _ask_from(use_case, "Europe/Madrid")

    [proposal] = [ev for ev in events if ev["type"] == "tool_approval_proposal"]
    assert proposal["arguments"] == {**MOVE.arguments, "title": "Test Event"}


@pytest.mark.asyncio
@pytest.mark.parametrize("lookup", [FakeEventLookup(fails=True), FakeEventLookup(), None])
async def test_GIVEN_the_event_cannot_be_looked_up_WHEN_its_card_is_shown_THEN_it_is_the_card_as_proposed(lookup):
    use_case, _, _, _ = _setup([[LLMResponseChunk(tool_calls=[REMOVE])]], event_lookup=lookup)

    events = await _ask_from(use_case, "Europe/Madrid")

    [proposal] = [ev for ev in events if ev["type"] == "tool_approval_proposal"]
    assert proposal["arguments"] == REMOVE.arguments
    assert events[-1]["type"] == "awaiting_approval"


@pytest.mark.asyncio
async def test_GIVEN_an_add_WHEN_its_card_is_shown_THEN_nothing_is_looked_up():
    lookup = FakeEventLookup([_test_event()])
    use_case, _, _, _ = _setup([[LLMResponseChunk(tool_calls=[DINNER])]], event_lookup=lookup)

    await _ask(use_case)

    assert lookup.asked == []


@pytest.mark.asyncio
async def test_GIVEN_a_named_remove_WHEN_approved_THEN_it_runs_as_proposed_and_its_step_names_the_event():
    use_case, session_repo, llm_client, tool_executor = _setup(
        [[LLMResponseChunk(tool_calls=[REMOVE])], [LLMResponseChunk(delta_content="Removed.")]],
        event_lookup=FakeEventLookup([_test_event()]),
    )
    await _ask_from(use_case, "Europe/Madrid")

    events = await _decide(use_case, "d1", approved=True)

    assert tool_executor.executed_calls[0]["args"] == REMOVE.arguments
    [result] = [ev for ev in events if ev["type"] == "tool_result"]
    assert result["data"]["summary"]["title"] == "Test Event"
    resumed = llm_client.stream_calls[1]["messages"]
    assert resumed[-2].tool_calls[0].arguments == REMOVE.arguments


@pytest.mark.asyncio
async def test_GIVEN_a_named_change_WHEN_the_member_renames_it_on_the_card_THEN_only_their_change_is_added():
    use_case, _, _, tool_executor = _setup(
        [[LLMResponseChunk(tool_calls=[MOVE])], [LLMResponseChunk(delta_content="Moved.")]],
        event_lookup=FakeEventLookup([_test_event()]),
    )
    await _ask_from(use_case, "Europe/Madrid")

    await _decide(use_case, "u1", approved=True, modified_arguments={"title": "Team sync"})

    assert tool_executor.executed_calls[0]["args"] == {**MOVE.arguments, "title": "Team sync"}


@pytest.mark.asyncio
async def test_GIVEN_a_named_remove_WHEN_declined_THEN_the_declined_step_names_the_event():
    use_case, _, _, _ = _setup(
        [[LLMResponseChunk(tool_calls=[REMOVE])], [LLMResponseChunk(delta_content="Kept it.")]],
        event_lookup=FakeEventLookup([_test_event()]),
    )
    await _ask_from(use_case, "Europe/Madrid")

    events = await _decide(use_case, "d1", approved=False)

    [declined] = [ev for ev in events if ev["type"] == "tool_declined"]
    assert declined["summary"] == {"action": "delete", "title": "Test Event"}


# One date of a repeating event: the card is about that date, not the series' first one (#63).
def _remove_of_series(**arguments):
    return LLMToolCall(id="s1", name="calendar_write", arguments={"action": "delete", "event_id": "series-1", **arguments})


def _second_date():
    start = datetime(2026, 10, 13, 8, 0, tzinfo=timezone.utc)
    return CalendarEvent(
        id="series-1",
        title="Series test",
        start_time=start,
        end_time=datetime(2026, 10, 13, 8, 30, tzinfo=timezone.utc),
        repeat=Repeat(frequency="weekly", count=4),
        occurrence_start=start,
    )


@pytest.mark.asyncio
async def test_GIVEN_a_remove_of_one_date_of_a_series_WHEN_its_card_is_shown_THEN_it_is_about_that_date():
    # The model writes the member's clock time and calls it UTC; the hub reads it as their clock time.
    remove = _remove_of_series(occurrence_start="2026-10-13T10:00:00Z", scope="this")
    lookup = FakeEventLookup([_second_date()])
    use_case, _, _, tool_executor = _setup(
        [[LLMResponseChunk(tool_calls=[remove])], [LLMResponseChunk(delta_content="Removed.")]], event_lookup=lookup
    )

    events = await _ask_from(use_case, "Europe/Madrid")

    assert lookup.dates == [datetime(2026, 10, 13, 10, 0, tzinfo=ZoneInfo("Europe/Madrid"))]
    [proposal] = [ev for ev in events if ev["type"] == "tool_approval_proposal"]
    assert proposal["arguments"] == {
        **remove.arguments,
        "title": "Series test",
        "start_time": "2026-10-13T10:00:00+02:00",
        "end_time": "2026-10-13T10:30:00+02:00",
        "is_all_day": False,
        "repeat": {"frequency": "weekly", "interval": 1, "count": 4},
    }

    await _decide(use_case, "s1", approved=True)

    assert tool_executor.executed_calls[0]["args"] == remove.arguments


@pytest.mark.asyncio
async def test_GIVEN_a_remove_of_a_whole_series_WHEN_its_card_is_shown_THEN_no_one_date_is_looked_up():
    remove = _remove_of_series(occurrence_start="2026-10-13T10:00:00", scope="all")
    lookup = FakeEventLookup([_second_date()])
    use_case, _, _, _ = _setup([[LLMResponseChunk(tool_calls=[remove])]], event_lookup=lookup)

    await _ask_from(use_case, "Europe/Madrid")

    assert lookup.dates == [None]


@pytest.mark.asyncio
async def test_GIVEN_a_date_that_is_not_a_time_WHEN_its_card_is_shown_THEN_it_is_the_card_as_proposed():
    remove = _remove_of_series(occurrence_start="the second one", scope="this")
    lookup = FakeEventLookup([_second_date()])
    use_case, _, _, _ = _setup([[LLMResponseChunk(tool_calls=[remove])]], event_lookup=lookup)

    events = await _ask_from(use_case, "Europe/Madrid")

    [proposal] = [ev for ev in events if ev["type"] == "tool_approval_proposal"]
    assert proposal["arguments"] == remove.arguments
    assert events[-1]["type"] == "awaiting_approval"
    assert lookup.asked == []
