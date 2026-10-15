"""
A chat takes its next message as soon as the answer is sent (#53).

After a turn says `done`, the hub may still have work to do for it: the history summary, a further
model call. That work used to run while the chat's lock was held, so a member who replied quickly
got 409 Conflict for up to a minute. It now runs after the lock is let go, one chat's at a time,
and the stream ends with the answer. Memories are no longer written after a turn at all: the
reflection sweep reads the chat once it goes quiet (memory spec §2.2).

Only the model is stood in for here: the routers, the background runners and the use cases between
them are the real ones.
"""
import asyncio

import httpx
import pytest
import pytest_asyncio

from app.bootstrap.di import _after_turn_work, _ollama_connector
from app.domain.entities.llm_message import LLMResponse, LLMResponseChunk
from tests.auth_helpers import register_admin


async def _session(client: httpx.AsyncClient) -> tuple[dict, str]:
    token, _ = await register_admin(client, full_name="Admin")
    auth = {"Authorization": f"Bearer {token}"}
    agent = await client.get("/api/v1/agents/assistant", headers=auth)
    created = await client.post(
        "/api/v1/sessions", json={"agent_id": agent.json()["id"], "title": "Dinner"}, headers=auth
    )
    return auth, created.json()["id"]


async def _stream(client: httpx.AsyncClient, session_id: str, auth: dict, content: str) -> httpx.Response:
    # Bounded: before #53 the stream stayed open until the work after the turn was done.
    return await asyncio.wait_for(
        client.post(f"/api/v1/sessions/{session_id}/chat/stream", json={"content": content}, headers=auth),
        timeout=2.0,
    )


class SlowSummaryModel:
    """Answers turns at once; the history summary waits until the test lets it finish."""

    SUMMARY = "They talked about the pantry."

    def __init__(self):
        self.release_summary = asyncio.Event()
        self.summaries_running = 0
        self.turn_prompts: list[str] = []

    async def chat_completion(self, messages, model, temperature=0.7, top_p=0.9, tools=None, keep_alive=None):
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

    async def until(self, condition, timeout=2.0):
        async def wait():
            while not condition():
                await asyncio.sleep(0.01)
        await asyncio.wait_for(wait(), timeout)


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


@pytest_asyncio.fixture
async def summarizing(client: httpx.AsyncClient, monkeypatch: pytest.MonkeyPatch):
    """A chat long enough that its next turn sets a slow summary going."""
    from app.core.config import settings

    slow = SlowSummaryModel()
    monkeypatch.setattr(_ollama_connector, "chat_completion", slow.chat_completion)
    monkeypatch.setattr(_ollama_connector, "stream_chat_completion", slow.stream_chat_completion)
    monkeypatch.setattr(settings, "HISTORY_TOKENS", 30)
    auth, session_id = await _session(client)
    for role, content in [
        ("user", "Can you keep the shopping list for this week, please?"),
        ("assistant", "Of course. Tell me what we need and I will keep track of it."),
    ]:
        await client.post(
            f"/api/v1/sessions/{session_id}/messages", json={"role": role, "content": content}, headers=auth
        )
    yield slow, auth, session_id
    slow.release_summary.set()
    await slow.until(lambda: _after_turn_work.running == 0)


@pytest.mark.asyncio
async def test_the_stream_ends_with_the_answer_while_the_summary_still_runs(client: httpx.AsyncClient, summarizing):
    """GIVEN a summary that has not finished WHEN a turn is streamed THEN the stream still ends at done."""
    slow, auth, session_id = summarizing

    response = await _stream(client, session_id, auth, "We're out of rice, and the pasta is nearly gone as well.")

    assert response.status_code == 200
    assert '"type": "done"' in response.text or '"type":"done"' in response.text
    await slow.until(lambda: slow.summaries_running == 1)


@pytest.mark.asyncio
async def test_the_chat_is_not_reported_busy_once_the_answer_is_sent(client: httpx.AsyncClient, summarizing):
    """GIVEN the summary still running THEN the phone is told no turn is being generated."""
    slow, auth, session_id = summarizing
    await _stream(client, session_id, auth, "We're out of rice, and the pasta is nearly gone as well.")
    await slow.until(lambda: slow.summaries_running == 1)

    read = await client.get(f"/api/v1/sessions/{session_id}", headers=auth)

    assert read.json()["turn_running"] is False
