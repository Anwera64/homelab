"""
Connecting a calendar marks the calendar steps that failed for want of one as fixed (slice 4, PR 7).

The phone draws a failed step's fix card from what the hub saved with it. Once a calendar connects,
those cards should say so, in every chat and for good: a later failure is a new step with a card
of its own. Nothing else in an answer is touched.
"""

import copy
from typing import Dict, List

import pytest

from app.domain.entities.session import ChatMessage
from app.domain.use_cases.integrations.mark_calendar_steps_fixed import MarkCalendarStepsFixedUseCase


def _part(tool="calendar_write", success=False, reason="calendar_rejected", **summary):
    part = {"type": "tool", "tool": tool, "success": success}
    if reason is not None:
        part["summary"] = {"reason": reason, **summary}
    return part


class FakeSessionRepository:
    """The two calls the use case makes, over messages kept per member."""

    def __init__(self, messages_by_user: Dict[str, List[ChatMessage]]):
        self.messages_by_user = messages_by_user
        self.updated: List[ChatMessage] = []

    async def list_assistant_messages_mentioning(self, user_id: str, needles: List[str]) -> List[ChatMessage]:
        return [
            copy.deepcopy(m)
            for m in self.messages_by_user.get(user_id, [])
            if m.role == "assistant" and any(n in str(m.metadata_json) for n in needles)
        ]

    async def update_message(self, message: ChatMessage) -> ChatMessage:
        self.updated.append(message)
        return message


def _answer(message_id: str, *parts, **metadata) -> ChatMessage:
    return ChatMessage(id=message_id, session_id="s-1", role="assistant", content="", metadata_json={"parts": list(parts), **metadata})


@pytest.mark.asyncio
async def test_GIVEN_calendar_steps_that_failed_on_the_sign_in_or_with_no_calendar_WHEN_a_calendar_connects_THEN_each_is_marked_fixed():
    repo = FakeSessionRepository(
        {
            "emma": [
                _answer("a-1", _part(reason="calendar_rejected", action="create"), {"type": "text", "content": "Sorry."}),
                _answer("a-2", _part(tool="calendar_read", reason="calendar_not_connected")),
            ]
        }
    )

    await MarkCalendarStepsFixedUseCase(repo).execute("emma")

    by_id = {m.id: m for m in repo.updated}
    assert by_id["a-1"].metadata_json["parts"][0]["summary"] == {"reason": "calendar_rejected", "action": "create", "fixed": True}
    assert by_id["a-1"].metadata_json["parts"][1] == {"type": "text", "content": "Sorry."}
    assert by_id["a-2"].metadata_json["parts"][0]["summary"]["fixed"] is True


@pytest.mark.asyncio
async def test_GIVEN_steps_that_failed_for_other_reasons_or_did_not_fail_WHEN_a_calendar_connects_THEN_they_are_left_alone():
    untouched = [
        _part(reason="calendar_unreachable_or_other"),
        _part(tool="read_page", reason="calendar_rejected"),
        _part(success=True, reason=None),
    ]
    repo = FakeSessionRepository({"emma": [_answer("a-1", *untouched, _part(reason="calendar_rejected"))]})

    await MarkCalendarStepsFixedUseCase(repo).execute("emma")

    parts = repo.updated[0].metadata_json["parts"]
    assert parts[:3] == untouched
    assert parts[3]["summary"]["fixed"] is True


@pytest.mark.asyncio
async def test_GIVEN_an_answer_with_nothing_to_fix_WHEN_a_calendar_connects_THEN_it_is_not_saved_again():
    already = _part(reason="calendar_rejected", fixed=True)
    repo = FakeSessionRepository(
        {"emma": [_answer("a-1", already), _answer("a-2", _part(tool="read_page", reason="calendar_rejected"))]}
    )

    await MarkCalendarStepsFixedUseCase(repo).execute("emma")

    assert repo.updated == []


@pytest.mark.asyncio
async def test_GIVEN_another_members_failed_step_WHEN_a_calendar_connects_THEN_it_stays_as_it_was():
    repo = FakeSessionRepository({"emma": [], "liam": [_answer("a-9", _part(reason="calendar_rejected"))]})

    await MarkCalendarStepsFixedUseCase(repo).execute("emma")

    assert repo.updated == []
