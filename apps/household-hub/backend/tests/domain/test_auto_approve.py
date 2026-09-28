"""
Auto-approving a write from its card (slice 4, PR 5).

Each member chooses, per tool and action, whether agents may do it without asking. A write the member
auto-approves runs straight away and its record says it ran automatically. Removing an event and
replacing a note always ask: the settings refuse them, and the turn asks whatever is saved.
"""
from typing import Dict, List, Tuple

import pytest

from app.domain.entities.llm_message import LLMResponseChunk, LLMToolCall
from app.domain.entities.tool_approval import ToolApproval
from app.domain.exceptions import AlwaysAsksException, InvalidOperationException
from app.domain.use_cases.chat.tool_approval_settings import (
    ListToolApprovalsUseCase,
    SetToolApprovalUseCase,
)
from tests.domain.test_chat_turn import FakeUnitOfWork
from tests.domain.test_tool_approval import DINNER, USER, _answers, _ask, _decide, _setup


class FakeToolApprovalRepository:
    def __init__(self, auto: List[Tuple[str, str, str]] = ()):
        self.saved: Dict[Tuple[str, str, str], bool] = {key: True for key in auto}

    async def list_for_user(self, user_id: str) -> List[ToolApproval]:
        return [
            ToolApproval(tool=tool, action=action, auto=auto)
            for (owner, tool, action), auto in self.saved.items()
            if owner == user_id
        ]

    async def set(self, user_id: str, tool: str, action: str, auto: bool) -> None:
        self.saved[(user_id, tool, action)] = auto


REMOVE = LLMToolCall(id="c3", name="calendar_write", arguments={"action": "delete", "event_id": "e1", "title": "Dinner together"})
REPLACE = LLMToolCall(id="c4", name="document_writer", arguments={"action": "replace", "title": "Shopping", "content": "Eggs"})


def _auto_setup(rounds, auto=()):
    approvals = FakeToolApprovalRepository(auto)
    use_case, session_repo, llm_client, tool_executor = _setup(rounds, approvals=approvals)
    return use_case, session_repo, llm_client, tool_executor, approvals


@pytest.mark.asyncio
async def test_GIVEN_adding_events_is_automatic_WHEN_the_model_adds_one_THEN_it_runs_with_no_card_and_an_automatic_record():
    use_case, session_repo, _, tool_executor, _ = _auto_setup(
        [[LLMResponseChunk(tool_calls=[DINNER])], [LLMResponseChunk(delta_content="Added.")]],
        auto=[("u1", "calendar_write", "create")],
    )

    events = await _ask(use_case)

    assert not any(ev["type"] == "tool_approval_proposal" for ev in events)
    assert [call["tool"] for call in tool_executor.executed_calls] == ["calendar_write"]
    [result] = [ev for ev in events if ev["type"] == "tool_result"]
    assert result["data"]["auto"] is True
    assert events[-1]["type"] == "done"
    [answer] = _answers(session_repo)
    tool_part = answer.metadata_json["parts"][0]
    assert tool_part["type"] == "tool" and tool_part["auto"] is True


@pytest.mark.asyncio
async def test_GIVEN_only_adding_is_automatic_WHEN_the_model_changes_an_event_THEN_it_still_asks():
    change = LLMToolCall(id="c5", name="calendar_write", arguments={"action": "update", "event_id": "e1"})
    use_case, _, _, tool_executor, _ = _auto_setup(
        [[LLMResponseChunk(tool_calls=[change])]],
        auto=[("u1", "calendar_write", "create")],
    )

    events = await _ask(use_case)

    assert tool_executor.executed_calls == []
    assert events[-1]["type"] == "awaiting_approval"


@pytest.mark.asyncio
async def test_GIVEN_someone_elses_setting_WHEN_this_member_asks_for_a_write_THEN_it_asks():
    use_case, _, _, tool_executor, _ = _auto_setup(
        [[LLMResponseChunk(tool_calls=[DINNER])]],
        auto=[("u2", "calendar_write", "create")],
    )

    events = await _ask(use_case)

    assert tool_executor.executed_calls == []
    assert events[-1]["type"] == "awaiting_approval"


@pytest.mark.asyncio
@pytest.mark.parametrize("call", [REMOVE, REPLACE], ids=["remove", "replace"])
async def test_GIVEN_a_saved_auto_for_an_always_asks_action_WHEN_the_model_asks_for_it_THEN_it_still_asks(call):
    """A setting that slipped past the settings (an old row, a hand edit) never lets these through."""
    use_case, _, _, tool_executor, _ = _auto_setup(
        [[LLMResponseChunk(tool_calls=[call])]],
        auto=[("u1", call.name, call.arguments["action"])],
    )

    events = await _ask(use_case)

    assert tool_executor.executed_calls == []
    assert events[-1]["type"] == "awaiting_approval"


@pytest.mark.asyncio
async def test_GIVEN_an_approved_card_WHEN_the_turn_carries_on_THEN_the_write_is_not_marked_automatic():
    use_case, session_repo, _, _, _ = _auto_setup(
        [[LLMResponseChunk(tool_calls=[DINNER])], [LLMResponseChunk(delta_content="Done.")]]
    )
    await _ask(use_case)

    await _decide(use_case, "c1", approved=True)

    [answer] = _answers(session_repo)
    assert "auto" not in answer.metadata_json["parts"][0]


@pytest.mark.asyncio
async def test_GIVEN_adding_turned_automatic_on_the_card_WHEN_the_paused_turn_carries_on_THEN_its_next_add_runs_without_asking():
    """Ticking the box and approving applies to the rest of the same answer too."""
    flowers = LLMToolCall(id="c6", name="calendar_write", arguments={"action": "create", "title": "Buy flowers"})
    use_case, session_repo, _, tool_executor, approvals = _auto_setup(
        [
            [LLMResponseChunk(tool_calls=[DINNER])],
            [LLMResponseChunk(tool_calls=[flowers])],
            [LLMResponseChunk(delta_content="Both are in.")],
        ]
    )
    await _ask(use_case)
    await approvals.set("u1", "calendar_write", "create", True)

    events = await _decide(use_case, "c1", approved=True)

    assert [call["args"]["title"] for call in tool_executor.executed_calls] == ["Dinner together", "Buy flowers"]
    assert events[-1]["type"] == "done"


@pytest.mark.asyncio
async def test_GIVEN_no_settings_WHEN_listed_THEN_every_action_asks_and_removing_and_replacing_always_ask():
    listed = await ListToolApprovalsUseCase(FakeToolApprovalRepository()).execute(USER)

    assert [(a.tool, a.action, a.auto, a.always_asks) for a in listed] == [
        ("calendar_write", "create", False, False),
        ("calendar_write", "update", False, False),
        ("calendar_write", "delete", False, True),
        ("document_writer", "create", False, False),
        ("document_writer", "append", False, False),
        ("document_writer", "replace", False, True),
    ]


@pytest.mark.asyncio
async def test_GIVEN_adding_events_WHEN_turned_automatic_and_back_THEN_the_list_follows():
    repo = FakeToolApprovalRepository()
    set_approval = SetToolApprovalUseCase(repo, FakeUnitOfWork())

    after_tick = await set_approval.execute(USER, "calendar_write", "create", True)
    assert [(a.tool, a.action) for a in after_tick if a.auto] == [("calendar_write", "create")]

    after_undo = await set_approval.execute(USER, "calendar_write", "create", False)
    assert not any(a.auto for a in after_undo)


@pytest.mark.asyncio
@pytest.mark.parametrize("tool, action", [("calendar_write", "delete"), ("document_writer", "replace")])
async def test_GIVEN_an_always_asks_action_WHEN_turned_automatic_THEN_it_is_refused(tool, action):
    repo = FakeToolApprovalRepository()

    with pytest.raises(AlwaysAsksException):
        await SetToolApprovalUseCase(repo, FakeUnitOfWork()).execute(USER, tool, action, True)

    assert repo.saved == {}


@pytest.mark.asyncio
@pytest.mark.parametrize("tool, action", [("calendar_read", "read"), ("calendar_write", "move")])
async def test_GIVEN_an_action_no_tool_has_WHEN_set_THEN_it_is_refused(tool, action):
    repo = FakeToolApprovalRepository()

    with pytest.raises(InvalidOperationException):
        await SetToolApprovalUseCase(repo, FakeUnitOfWork()).execute(USER, tool, action, True)

    assert repo.saved == {}
