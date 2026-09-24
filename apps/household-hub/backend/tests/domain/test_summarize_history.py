"""
Older messages are folded into a real summary instead of being cut to their first 100 characters.

The summary is written after a turn, in the background, and only once the messages it doesn't
cover yet outgrow their budget; then enough of the oldest are folded in to bring the rest down to
half of it, so it isn't rewritten after every turn.
"""
from typing import List, Optional

import pytest

from app.domain.entities.llm_message import LLMResponse
from app.domain.entities.session import ChatMessage, ConversationSession
from app.domain.exceptions import LLMInferenceException
from app.domain.use_cases.chat.summarize_history import SummarizeHistoryUseCase
from app.domain.use_cases.chat.token_estimate import estimate_tokens


class FakeSessionRepository:
    def __init__(self, session: ConversationSession, messages: List[ChatMessage]):
        self.session = session
        self.messages = messages
        self.saved: List[tuple] = []

    async def get_by_id(self, session_id: str) -> Optional[ConversationSession]:
        return self.session if session_id == self.session.id else None

    async def get_messages_after(self, session_id, after_id, limit=200):
        ids = [m.id for m in self.messages]
        start = ids.index(after_id) + 1 if after_id in ids else 0
        return self.messages[start:][-limit:]

    async def save_history_summary(self, session_id, summary, through_id):
        self.saved.append((session_id, summary, through_id))


class FakeLLMClient:
    def __init__(self, reply: str = "Alex asked about tariffs; steel duties rose 25%.", fail: bool = False):
        self.reply = reply
        self.fail = fail
        self.calls = []

    async def chat_completion(self, messages, model, temperature=0.7, top_p=0.9, tools=None):
        self.calls.append({"messages": messages, "model": model, "temperature": temperature, "tools": tools})
        if self.fail:
            raise LLMInferenceException("LLM inference timed out")
        return LLMResponse(content=self.reply)


class FakeResolver:
    async def default(self):
        return "qwen3.8-rvn"


class FakeUnitOfWork:
    def __init__(self):
        self.commits = 0

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        pass

    async def commit(self):
        self.commits += 1


def _messages(count: int, characters: int) -> List[ChatMessage]:
    return [
        ChatMessage(id=f"m{i}", session_id="s1", role="user" if i % 2 else "assistant", content=f"{i}:" + "x" * characters)
        for i in range(1, count + 1)
    ]


def _use_case(repo, llm, uow=None, history_tokens=1000):
    return SummarizeHistoryUseCase(
        session_repo=repo,
        llm_client=llm,
        model_resolver=FakeResolver(),
        uow=uow or FakeUnitOfWork(),
        history_tokens=history_tokens,
    )


@pytest.mark.asyncio
async def test_GIVEN_history_within_its_budget_WHEN_summarizing_THEN_the_model_is_not_asked():
    repo = FakeSessionRepository(ConversationSession(id="s1"), _messages(4, 300))
    llm = FakeLLMClient()

    assert await _use_case(repo, llm).execute("s1") is False
    assert llm.calls == [] and repo.saved == []


@pytest.mark.asyncio
async def test_GIVEN_history_over_its_budget_WHEN_summarizing_THEN_the_oldest_are_folded_until_the_rest_is_half():
    # Ten messages of about 200 tokens each against a budget of 1,000.
    messages = _messages(10, 600)
    repo = FakeSessionRepository(ConversationSession(id="s1"), messages)
    llm = FakeLLMClient()
    uow = FakeUnitOfWork()

    assert await _use_case(repo, llm, uow).execute("s1") is True

    [(session_id, summary, through_id)] = repo.saved
    assert (session_id, summary) == ("s1", "Alex asked about tariffs; steel duties rose 25%.")
    folded = messages[: [m.id for m in messages].index(through_id) + 1]
    rest = messages[len(folded):]
    assert sum(estimate_tokens(m.content) for m in rest) <= 500
    assert sum(estimate_tokens(m.content) for m in messages[len(folded) - 1:]) > 500
    # The folded messages are what the model was asked to summarise, whole.
    prompt = "\n".join(m.content for m in llm.calls[0]["messages"])
    assert all(m.content in prompt for m in folded)
    assert not any(m.content in prompt for m in rest)
    assert llm.calls[0]["tools"] is None and llm.calls[0]["temperature"] == 0.1
    assert uow.commits == 1


@pytest.mark.asyncio
async def test_GIVEN_an_earlier_summary_WHEN_summarizing_again_THEN_it_is_folded_forward_with_the_new_messages():
    messages = _messages(12, 600)
    session = ConversationSession(id="s1", history_summary="Earlier: Alex planned a trip to Lima.", summarized_through_id="m2")
    repo = FakeSessionRepository(session, messages)
    llm = FakeLLMClient()

    await _use_case(repo, llm).execute("s1")

    prompt = "\n".join(m.content for m in llm.calls[0]["messages"])
    assert "Earlier: Alex planned a trip to Lima." in prompt
    assert messages[0].content not in prompt and messages[1].content not in prompt
    assert messages[2].content in prompt


@pytest.mark.asyncio
async def test_GIVEN_one_huge_last_exchange_WHEN_summarizing_THEN_the_newest_two_messages_stay_word_for_word():
    messages = _messages(4, 3000)
    repo = FakeSessionRepository(ConversationSession(id="s1"), messages)

    await _use_case(repo, FakeLLMClient()).execute("s1")

    [(_, _, through_id)] = repo.saved
    assert through_id == "m2"


@pytest.mark.asyncio
async def test_GIVEN_the_model_fails_WHEN_summarizing_THEN_nothing_is_saved():
    repo = FakeSessionRepository(ConversationSession(id="s1"), _messages(10, 600))

    assert await _use_case(repo, FakeLLMClient(fail=True)).execute("s1") is False
    assert repo.saved == []


@pytest.mark.asyncio
async def test_GIVEN_an_empty_summary_WHEN_summarizing_THEN_nothing_is_saved():
    repo = FakeSessionRepository(ConversationSession(id="s1"), _messages(10, 600))

    assert await _use_case(repo, FakeLLMClient(reply="   ")).execute("s1") is False
    assert repo.saved == []
