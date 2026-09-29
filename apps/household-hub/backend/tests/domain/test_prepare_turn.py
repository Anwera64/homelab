"""
What the chat and decision endpoints check before a turn starts, as use cases rather than helpers the
router calls itself (AGENTS.md §3: presentation holds thin routers; the rules live in domain).

A new message is refused while a card waits. A decision names a card that is still waiting on the
newest answer, and carries on the question that answer was for.
"""
import pytest

from app.domain.entities.session import PAUSED_TURN, ChatMessage, ConversationSession
from app.domain.entities.user import User
from app.domain.exceptions import (
    AlreadyDecidedException,
    ApprovalPendingException,
    EntityNotFoundException,
    ZeroLeakViolationException,
)
from app.domain.use_cases.chat.prepare_turn import PrepareChatTurnUseCase, PrepareToolDecisionUseCase
from app.domain.use_cases.sessions.get_session import GetSessionUseCase
from tests.domain.test_chat_turn import FakeSessionRepository


MEMBER = User(id="u1", full_name="Ana")


def _session(**kwargs) -> ConversationSession:
    return ConversationSession(id="s1", user_id="u1", agent_id="a1", **kwargs)


def _question(content: str = "Add dinner on Friday") -> ChatMessage:
    return ChatMessage(session_id="s1", role="user", content=content)


def _answer(*cards: dict, paused: bool = True) -> ChatMessage:
    metadata = {"parts": [{"type": "text", "content": "Sure."}, *cards]}
    if paused:
        metadata[PAUSED_TURN] = {"messages": [], "round": 0}
    return ChatMessage(session_id="s1", role="assistant", content="Sure.", metadata_json=metadata)


def _card(tool_call_id: str = "call-1", status: str = "pending") -> dict:
    return {"type": "proposal", "tool_call_id": tool_call_id, "tool": "calendar_write", "status": status}


def _prepare_chat(*messages: ChatMessage, session=None) -> PrepareChatTurnUseCase:
    repo = FakeSessionRepository(sessions=[session or _session()], messages=list(messages))
    return PrepareChatTurnUseCase(GetSessionUseCase(repo))


def _prepare_decision(*messages: ChatMessage, session=None) -> PrepareToolDecisionUseCase:
    repo = FakeSessionRepository(sessions=[session or _session()], messages=list(messages))
    return PrepareToolDecisionUseCase(GetSessionUseCase(repo))


# A new message


@pytest.mark.asyncio
async def test_a_new_message_gets_the_session_and_what_was_said():
    question, answer = _question(), _answer(paused=False)

    session, messages = await _prepare_chat(question, answer).execute("s1", MEMBER)

    assert session.id == "s1"
    assert messages == [question, answer]


@pytest.mark.asyncio
async def test_a_new_message_waits_while_a_card_does():
    with pytest.raises(ApprovalPendingException):
        await _prepare_chat(_question(), _answer(_card())).execute("s1", MEMBER)


@pytest.mark.asyncio
async def test_a_new_message_goes_ahead_once_every_card_is_answered():
    _, messages = await _prepare_chat(_question(), _answer(_card(status="approved"))).execute("s1", MEMBER)

    assert len(messages) == 2


@pytest.mark.asyncio
async def test_a_new_message_in_someone_elses_chat_is_refused():
    with pytest.raises(ZeroLeakViolationException):
        await _prepare_chat(session=_session()).execute("s1", User(id="u2", full_name="Bo"))


# A decision


@pytest.mark.asyncio
async def test_a_decision_carries_on_the_question_the_answer_was_for():
    decision = await _prepare_decision(
        _question("Earlier"), _answer(paused=False), _question("Add dinner on Friday"), _answer(_card())
    ).execute("s1", MEMBER, "call-1")

    assert decision.question == "Add dinner on Friday"
    assert decision.agent_id == "a1"


@pytest.mark.asyncio
async def test_a_decision_in_a_chat_without_an_agent_has_no_agent_id():
    decision = await _prepare_decision(_question(), _answer(_card()), session=_session_without_agent()).execute(
        "s1", MEMBER, "call-1"
    )

    assert decision.agent_id == ""


def _session_without_agent() -> ConversationSession:
    return ConversationSession(id="s1", user_id="u1", agent_id=None)


@pytest.mark.asyncio
async def test_a_decision_for_a_card_that_is_not_there_is_not_found():
    with pytest.raises(EntityNotFoundException):
        await _prepare_decision(_question(), _answer(_card("call-1"))).execute("s1", MEMBER, "call-9")


@pytest.mark.asyncio
async def test_a_decision_for_an_answered_card_is_refused():
    with pytest.raises(AlreadyDecidedException):
        await _prepare_decision(_question(), _answer(_card(status="declined"), _card("call-2"))).execute(
            "s1", MEMBER, "call-1"
        )


@pytest.mark.asyncio
async def test_a_decision_in_someone_elses_chat_is_refused():
    with pytest.raises(ZeroLeakViolationException):
        await _prepare_decision(_question(), _answer(_card())).execute(
            "s1", User(id="u2", full_name="Bo"), "call-1"
        )


# What the member is shown of an answer


def test_the_member_is_never_shown_what_a_paused_turn_keeps_to_carry_on():
    answer = _answer(_card())

    shown = answer.metadata_for_member()

    assert PAUSED_TURN not in shown
    assert shown["parts"] == answer.metadata_json["parts"]
    assert PAUSED_TURN in answer.metadata_json


def test_an_answer_without_metadata_shows_none():
    assert ChatMessage(role="assistant", metadata_json=None).metadata_for_member() == {}
