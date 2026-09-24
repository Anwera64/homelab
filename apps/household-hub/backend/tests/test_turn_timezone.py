"""
The phone says which timezone it is in, and every answer is dated in it (#39).

The phone sends `X-Timezone` with every request. Plain, streamed and regenerated turns all carry it
to the agent's prompt and on to reflection, so "today" means the member's today. Without the header
the hub dates the prompt in UTC and says so.

Only the model is stood in for here: the routers, the background runners and the use cases between
them are the real ones.
"""
import asyncio

import httpx
import pytest

from app.bootstrap.di import _ollama_connector
from app.domain.entities.llm_message import LLMResponse, LLMResponseChunk
from app.domain.use_cases.memories.reflect_turn import EXTRACTION_SYSTEM_PROMPT
from tests.auth_helpers import register_admin

TOKYO = {"X-Timezone": "Asia/Tokyo"}


class RecordingModel:
    """Answers every call, and keeps the system prompts of chat turns and of reflection apart."""

    def __init__(self):
        self.turn_prompts: list[str] = []
        self.reflection_prompts: list[str] = []

    def _record(self, messages):
        system = "\n\n".join(m.content for m in messages if m.role == "system")
        if EXTRACTION_SYSTEM_PROMPT in system:
            self.reflection_prompts.append(system)
        else:
            self.turn_prompts.append(system)

    async def chat_completion(self, messages, model, temperature=0.7, top_p=0.9, tools=None):
        self._record(messages)
        if EXTRACTION_SYSTEM_PROMPT in messages[0].content:
            return LLMResponse(content='{"memories": [], "milestones": []}')
        return LLMResponse(content="It's a fine day.")

    async def stream_chat_completion(self, messages, model, temperature=0.7, top_p=0.9, tools=None):
        self._record(messages)
        yield LLMResponseChunk(delta_content="It's a fine day.")

    async def reflected(self) -> list[str]:
        """Reflection runs after the answer is sent, so give it a moment to arrive."""
        for _ in range(100):
            if self.reflection_prompts:
                break
            await asyncio.sleep(0.01)
        return self.reflection_prompts


@pytest.fixture
def model(monkeypatch: pytest.MonkeyPatch) -> RecordingModel:
    recording = RecordingModel()
    monkeypatch.setattr(_ollama_connector, "chat_completion", recording.chat_completion)
    monkeypatch.setattr(_ollama_connector, "stream_chat_completion", recording.stream_chat_completion)
    return recording


async def _session(client: httpx.AsyncClient) -> tuple[dict, str]:
    token, _ = await register_admin(client, full_name="Admin")
    auth = {"Authorization": f"Bearer {token}"}
    agent = await client.get("/api/v1/agents/assistant", headers=auth)
    created = await client.post(
        "/api/v1/sessions", json={"agent_id": agent.json()["id"], "title": "What day is it"}, headers=auth
    )
    return auth, created.json()["id"]


@pytest.mark.asyncio
async def test_a_streamed_turn_is_dated_in_the_phones_timezone(client: httpx.AsyncClient, model: RecordingModel):
    auth, session_id = await _session(client)

    response = await client.post(
        f"/api/v1/sessions/{session_id}/chat/stream", json={"content": "What's on today?"}, headers=auth | TOKYO
    )

    assert response.status_code == 200
    assert "(Asia/Tokyo)." in model.turn_prompts[0]
    assert "(Asia/Tokyo)." in (await model.reflected())[0]


@pytest.mark.asyncio
async def test_a_regenerated_answer_is_dated_in_the_phones_timezone(client: httpx.AsyncClient, model: RecordingModel):
    auth, session_id = await _session(client)
    await client.post(
        f"/api/v1/sessions/{session_id}/messages", json={"role": "user", "content": "What's on today?"}, headers=auth
    )

    response = await client.post(f"/api/v1/sessions/{session_id}/chat/regenerate", headers=auth | TOKYO)

    assert response.status_code == 200
    assert "(Asia/Tokyo)." in model.turn_prompts[0]
    assert "(Asia/Tokyo)." in (await model.reflected())[0]


@pytest.mark.asyncio
async def test_a_plain_turn_is_dated_in_the_phones_timezone(client: httpx.AsyncClient, model: RecordingModel):
    auth, session_id = await _session(client)

    response = await client.post(
        f"/api/v1/sessions/{session_id}/chat", json={"content": "What's on today?"}, headers=auth | TOKYO
    )

    assert response.status_code == 200
    assert "(Asia/Tokyo)." in model.turn_prompts[0]
    assert "(Asia/Tokyo)." in (await model.reflected())[0]


@pytest.mark.asyncio
async def test_without_the_header_the_turn_is_dated_in_utc(client: httpx.AsyncClient, model: RecordingModel):
    auth, session_id = await _session(client)

    await client.post(f"/api/v1/sessions/{session_id}/chat/stream", json={"content": "What's on today?"}, headers=auth)

    assert "(UTC)." in model.turn_prompts[0]
    assert "(UTC)." in (await model.reflected())[0]
