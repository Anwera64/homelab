"""
A chat takes its next message as soon as the answer is sent (#53).

After a turn says `done`, the hub still has work to do for it: reflection, then the history
summary, each a further model call. That work used to run while the chat's lock was held, so a
member who replied quickly got 409 Conflict for up to a minute. It now runs after the lock is let
go, one chat's at a time, and the stream ends with the answer.

Only the model is stood in for here: the routers, the background runners and the use cases between
them are the real ones.
"""
import asyncio

import httpx
import pytest
import pytest_asyncio

from app.bootstrap.di import _after_turn_work, _ollama_connector
from app.domain.entities.llm_message import LLMResponse, LLMResponseChunk
from app.domain.use_cases.memories.reflect_turn import EXTRACTION_SYSTEM_PROMPT
from tests.auth_helpers import register_admin


class SlowReflectionModel:
    """Answers turns at once; reflection waits until the test lets it finish."""

    def __init__(self):
        self.release_reflection = asyncio.Event()
        self.turns = 0
        self.reflections_running = 0
        self.most_reflections_at_once = 0
        self.reflections_done = 0

    async def chat_completion(self, messages, model, temperature=0.7, top_p=0.9, tools=None):
        if EXTRACTION_SYSTEM_PROMPT in messages[0].content:
            self.reflections_running += 1
            self.most_reflections_at_once = max(self.most_reflections_at_once, self.reflections_running)
            try:
                await self.release_reflection.wait()
            finally:
                self.reflections_running -= 1
            self.reflections_done += 1
            return LLMResponse(content='{"memories": [], "milestones": []}')
        return LLMResponse(content="Noted.")

    async def stream_chat_completion(self, messages, model, temperature=0.7, top_p=0.9, tools=None):
        self.turns += 1
        yield LLMResponseChunk(delta_content="Noted.")

    async def until(self, condition, timeout=2.0):
        async def wait():
            while not condition():
                await asyncio.sleep(0.01)
        await asyncio.wait_for(wait(), timeout)


@pytest_asyncio.fixture
async def model(monkeypatch: pytest.MonkeyPatch) -> SlowReflectionModel:
    slow = SlowReflectionModel()
    monkeypatch.setattr(_ollama_connector, "chat_completion", slow.chat_completion)
    monkeypatch.setattr(_ollama_connector, "stream_chat_completion", slow.stream_chat_completion)
    yield slow
    # Let anything still waiting finish, so no reflection outlives the test's database.
    slow.release_reflection.set()
    await slow.until(lambda: _after_turn_work.running == 0)


async def _session(client: httpx.AsyncClient) -> tuple[dict, str]:
    token, _ = await register_admin(client, full_name="Admin")
    auth = {"Authorization": f"Bearer {token}"}
    agent = await client.get("/api/v1/agents/assistant", headers=auth)
    created = await client.post(
        "/api/v1/sessions", json={"agent_id": agent.json()["id"], "title": "Dinner"}, headers=auth
    )
    return auth, created.json()["id"]


async def _stream(client: httpx.AsyncClient, session_id: str, auth: dict, content: str) -> httpx.Response:
    # Bounded: before #53 the stream stayed open until reflection and the summary were done.
    return await asyncio.wait_for(
        client.post(f"/api/v1/sessions/{session_id}/chat/stream", json={"content": content}, headers=auth),
        timeout=2.0,
    )


@pytest.mark.asyncio
async def test_the_stream_ends_with_the_answer_while_reflection_still_runs(
    client: httpx.AsyncClient, model: SlowReflectionModel
):
    """GIVEN reflection that has not finished WHEN a turn is streamed THEN the stream still ends at done."""
    auth, session_id = await _session(client)

    response = await _stream(client, session_id, auth, "We're out of rice.")

    assert response.status_code == 200
    assert '"type": "done"' in response.text or '"type":"done"' in response.text
    await model.until(lambda: model.reflections_running == 1)


@pytest.mark.asyncio
async def test_a_second_message_right_after_the_answer_is_taken(
    client: httpx.AsyncClient, model: SlowReflectionModel
):
    """GIVEN the first turn's reflection still running WHEN the member writes again THEN it is answered."""
    auth, session_id = await _session(client)
    await _stream(client, session_id, auth, "We're out of rice.")
    await model.until(lambda: model.reflections_running == 1)

    second = await _stream(client, session_id, auth, "And pasta.")

    assert second.status_code == 200
    assert model.turns == 2


@pytest.mark.asyncio
async def test_the_chat_is_not_reported_busy_once_the_answer_is_sent(
    client: httpx.AsyncClient, model: SlowReflectionModel
):
    """GIVEN reflection still running THEN the phone is told no turn is being generated."""
    auth, session_id = await _session(client)
    await _stream(client, session_id, auth, "We're out of rice.")
    await model.until(lambda: model.reflections_running == 1)

    read = await client.get(f"/api/v1/sessions/{session_id}", headers=auth)

    assert read.json()["turn_running"] is False


@pytest.mark.asyncio
async def test_two_turns_reflections_run_one_after_the_other(
    client: httpx.AsyncClient, model: SlowReflectionModel
):
    """GIVEN two quick turns in one chat THEN their reflections never run at the same time, and both run."""
    auth, session_id = await _session(client)
    await _stream(client, session_id, auth, "We're out of rice.")
    await model.until(lambda: model.reflections_running == 1)
    await _stream(client, session_id, auth, "And pasta.")
    await asyncio.sleep(0.05)

    model.release_reflection.set()
    await model.until(lambda: model.reflections_done == 2)

    assert model.most_reflections_at_once == 1


class SlowSummaryModel:
    """Reflects at once; the history summary waits until the test lets it finish."""

    SUMMARY = "They talked about the pantry."

    def __init__(self):
        self.release_summary = asyncio.Event()
        self.summaries_running = 0
        self.turn_prompts: list[str] = []

    async def chat_completion(self, messages, model, temperature=0.7, top_p=0.9, tools=None):
        if EXTRACTION_SYSTEM_PROMPT in messages[0].content:
            return LLMResponse(content='{"memories": [], "milestones": []}')
        if "running summary" in messages[0].content:
            self.summaries_running += 1
            try:
                await self.release_summary.wait()
            finally:
                self.summaries_running -= 1
            return LLMResponse(content=self.SUMMARY)
        return LLMResponse(content="Noted.")

    async def stream_chat_completion(self, messages, model, temperature=0.7, top_p=0.9, tools=None):
        self.turn_prompts.append("\n\n".join(m.content for m in messages))
        yield LLMResponseChunk(delta_content="Noted, and I have put it on the shopping list for you.")

    until = SlowReflectionModel.until


@pytest.mark.asyncio
async def test_a_turn_during_the_summary_reads_only_what_is_saved(
    client: httpx.AsyncClient, monkeypatch: pytest.MonkeyPatch
):
    """
    GIVEN a history summary still being written WHEN the member writes again
    THEN the turn is answered from the saved history, with no summary in it until one is saved.
    """
    from app.core.config import settings

    slow = SlowSummaryModel()
    monkeypatch.setattr(_ollama_connector, "chat_completion", slow.chat_completion)
    monkeypatch.setattr(_ollama_connector, "stream_chat_completion", slow.stream_chat_completion)
    # Small enough that the chat below outgrows it, so the next turn sets a summary going.
    monkeypatch.setattr(settings, "HISTORY_TOKENS", 30)
    auth, session_id = await _session(client)
    for role, content in [
        ("user", "Can you keep the shopping list for this week, please?"),
        ("assistant", "Of course. Tell me what we need and I will keep track of it."),
    ]:
        await client.post(
            f"/api/v1/sessions/{session_id}/messages", json={"role": role, "content": content}, headers=auth
        )

    try:
        await _stream(client, session_id, auth, "We're out of rice, and the pasta is nearly gone as well.")
        await slow.until(lambda: slow.summaries_running == 1)

        second = await _stream(client, session_id, auth, "And the olive oil.")

        assert second.status_code == 200
        assert "[Earlier in this conversation]" not in slow.turn_prompts[1]
    finally:
        slow.release_summary.set()
        await slow.until(lambda: _after_turn_work.running == 0)

    await _stream(client, session_id, auth, "What was that list again?")
    assert f"[Earlier in this conversation]:\n{SlowSummaryModel.SUMMARY}" in slow.turn_prompts[2]
