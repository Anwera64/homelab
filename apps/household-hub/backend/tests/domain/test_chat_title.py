"""
A new chat is titled with the start of the member's first message, so it is recognisable at once;
reflection replaces it later (memory spec §2.3). A secret chat is titled the same way.
"""

import pytest

from app.domain.entities.agent import AgentPersonality
from app.domain.entities.llm_message import LLMResponse
from app.domain.entities.session import ChatMessage, ConversationSession
from app.domain.entities.user import User
from app.domain.use_cases.chat.chat_title import title_from_first_message
from app.domain.use_cases.chat.process_chat_turn import ProcessChatTurnUseCase
from tests.domain.test_chat_turn import (
    FakeAgentRepository,
    FakeContextAssembler,
    FakeLLMClient,
    FakeSessionRepository,
    FakeToolExecutor,
    FakeToolLister,
    FakeUnitOfWork,
    _house_resolver,
)


def test_GIVEN_a_short_message_WHEN_titled_THEN_it_is_kept_whole_on_one_line():
    assert title_from_first_message("  Plan dinners\nfor the week  ") == "Plan dinners for the week"


def test_GIVEN_a_long_message_WHEN_titled_THEN_it_is_cut_at_a_word_with_an_ellipsis():
    title = title_from_first_message("Can you plan dinners for the whole week, nothing with fish please")

    assert title == "Can you plan dinners for the whole week…"
    assert len(title) <= 41


def test_GIVEN_one_long_word_WHEN_titled_THEN_it_is_cut_where_it_must_be():
    assert title_from_first_message("x" * 60) == "x" * 40 + "…"


def _use_case(session_repo, llm_client):
    return ProcessChatTurnUseCase(
        session_repo=session_repo,
        agent_repo=FakeAgentRepository([AgentPersonality(id="a1", name="Planner")]),
        llm_client=llm_client,
        context_assembler=FakeContextAssembler(),
        tool_executor=FakeToolExecutor(),
        tool_lister=FakeToolLister(),
        model_resolver=_house_resolver(),
        uow=FakeUnitOfWork(),
    )


@pytest.mark.asyncio
@pytest.mark.parametrize("secret", [False, True])
async def test_GIVEN_a_new_chat_WHEN_the_first_message_is_sent_THEN_it_titles_the_chat(secret):
    session = ConversationSession(id="s1", user_id="u1", agent_id="a1", title="Chat with Planner", is_secret=secret)
    repo = FakeSessionRepository(sessions=[session])

    await _use_case(repo, FakeLLMClient([LLMResponse(content="Sure")])).execute(
        session_id="s1", current_user=User(id="u1", full_name="Emma"), content="Plan dinners for the week"
    )

    assert repo.sessions["s1"].title == "Plan dinners for the week"


@pytest.mark.asyncio
async def test_GIVEN_a_streamed_first_message_WHEN_sent_THEN_it_titles_the_chat():
    session = ConversationSession(id="s1", user_id="u1", agent_id="a1", title="Chat with Planner")
    repo = FakeSessionRepository(sessions=[session])

    async for _ in _use_case(repo, FakeLLMClient()).execute_stream(
        session_id="s1", current_user=User(id="u1", full_name="Emma"), content="Bins day?"
    ):
        pass

    assert repo.sessions["s1"].title == "Bins day?"


@pytest.mark.asyncio
async def test_GIVEN_a_chat_with_messages_WHEN_another_is_sent_THEN_the_title_stays():
    session = ConversationSession(id="s1", user_id="u1", agent_id="a1", title="Dinners this week")
    repo = FakeSessionRepository(
        sessions=[session],
        messages=[
            ChatMessage(session_id="s1", role="user", content="Plan dinners"),
            ChatMessage(session_id="s1", role="assistant", content="Sure"),
        ],
    )

    await _use_case(repo, FakeLLMClient([LLMResponse(content="Ok")])).execute(
        session_id="s1", current_user=User(id="u1", full_name="Emma"), content="And lunches?"
    )

    assert repo.sessions["s1"].title == "Dinners this week"
