"""
The sweep through the real routers, use cases and database (memory spec §2.2): a chat that went
quiet with the model loaded is reflected once; its memory shows on the memory page and its title
is replaced. Only the model is stood in for.
"""

import json
from datetime import datetime, timedelta, timezone

import httpx
import pytest

from app.bootstrap import di
from app.bootstrap.di import _ollama_connector
from app.core.config import settings
from app.domain.entities.llm_message import LLMResponse, LLMResponseChunk
from app.domain.use_cases.memories.reflect_chat import REFLECTION_SYSTEM_PROMPT
from app.domain.use_cases.memories.reflection_sweep import ReflectionSweepUseCase
from tests.auth_helpers import register_admin
from tests.conftest import TestingSessionLocal


class Model:
    def __init__(self):
        self.reflections = []

    async def chat_completion(self, messages, model, temperature=0.7, top_p=0.9, tools=None, keep_alive=None):
        if messages[0].content == REFLECTION_SYSTEM_PROMPT:
            self.reflections.append(keep_alive)
            return LLMResponse(content=json.dumps({
                "title": "Robin's diet",
                "facts": [{"content": "Robin is vegan", "category": "preference", "scope": "household",
                           "relevant_for": ["planning dinner"], "relation": "different"}],
            }))
        return LLMResponse(content="Noted.")

    async def stream_chat_completion(self, messages, model, temperature=0.7, top_p=0.9, tools=None):
        yield LLMResponseChunk(delta_content="Noted.")

    async def loaded_until(self, model):
        return datetime.now(timezone.utc) + timedelta(minutes=10)


async def _sweep():
    async with TestingSessionLocal() as session:
        await di.get_container(session)[ReflectionSweepUseCase].run_pass()


@pytest.mark.asyncio
async def test_GIVEN_a_quiet_chat_WHEN_swept_THEN_its_memory_and_title_appear_once(client: httpx.AsyncClient, monkeypatch):
    model = Model()
    monkeypatch.setattr(_ollama_connector, "chat_completion", model.chat_completion)
    monkeypatch.setattr(_ollama_connector, "stream_chat_completion", model.stream_chat_completion)
    monkeypatch.setattr(_ollama_connector, "loaded_until", model.loaded_until)
    monkeypatch.setattr(settings, "REFLECTION_IDLE_MINUTES", 0)
    token, _ = await register_admin(client, full_name="Emma")
    auth = {"Authorization": f"Bearer {token}"}
    agent = await client.get("/api/v1/agents/assistant", headers=auth)
    chat = (await client.post("/api/v1/sessions", json={"agent_id": agent.json()["id"]}, headers=auth)).json()
    await client.post(f"/api/v1/sessions/{chat['id']}/chat/stream", json={"content": "My sister Robin is vegan now"}, headers=auth)

    titled = (await client.get(f"/api/v1/sessions/{chat['id']}", headers=auth)).json()
    assert titled["title"] == "My sister Robin is vegan now"

    await _sweep()
    await _sweep()

    page = (await client.get("/api/v1/memories/page", headers=auth)).json()
    assert [item["content"] for item in page["household"]] == ["Robin is vegan"]
    assert len(model.reflections) == 1 and 0 < model.reflections[0] <= 600
    assert (await client.get(f"/api/v1/sessions/{chat['id']}", headers=auth)).json()["title"] == "Robin's diet"
