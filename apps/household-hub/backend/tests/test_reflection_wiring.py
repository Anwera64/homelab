"""
Reflection moves off the turn (memory spec §2.2, §10 "No reflection per turn"): a finished turn
starts no model call of its own, and the hub runs the sweep in the background instead.
"""

import asyncio
from contextlib import asynccontextmanager

import httpx
import pytest

from app.bootstrap import app_factory, di
from app.bootstrap.di import _after_turn_work, _ollama_connector
from app.core.config import Settings
from app.domain.entities.llm_message import LLMResponse, LLMResponseChunk
from app.domain.use_cases.memories.reflection_sweep import ReflectionSweepUseCase
from app.presentation.api import deps as pres_deps
from tests.auth_helpers import register_admin


class CountingModel:
    def __init__(self):
        self.calls = 0

    async def chat_completion(self, messages, model, temperature=0.7, top_p=0.9, tools=None, keep_alive=None):
        self.calls += 1
        return LLMResponse(content="Noted.")

    async def stream_chat_completion(self, messages, model, temperature=0.7, top_p=0.9, tools=None):
        yield LLMResponseChunk(delta_content="Noted.")


async def _settle():
    for _ in range(100):
        if _after_turn_work.running == 0:
            return
        await asyncio.sleep(0.01)


@pytest.mark.asyncio
async def test_GIVEN_a_finished_turn_WHEN_its_answer_is_sent_THEN_no_further_model_call_starts(
    client: httpx.AsyncClient, monkeypatch: pytest.MonkeyPatch
):
    model = CountingModel()
    monkeypatch.setattr(_ollama_connector, "chat_completion", model.chat_completion)
    monkeypatch.setattr(_ollama_connector, "stream_chat_completion", model.stream_chat_completion)
    token, _ = await register_admin(client, full_name="Emma")
    auth = {"Authorization": f"Bearer {token}"}
    agent = await client.get("/api/v1/agents/assistant", headers=auth)
    chat = (await client.post("/api/v1/sessions", json={"agent_id": agent.json()["id"]}, headers=auth)).json()

    streamed = await client.post(f"/api/v1/sessions/{chat['id']}/chat/stream", json={"content": "Out of rice"}, headers=auth)
    plain = await client.post(f"/api/v1/sessions/{chat['id']}/chat", json={"content": "And pasta"}, headers=auth)
    await _settle()

    assert streamed.status_code == 200 and plain.status_code == 200
    assert model.calls == 1  # the plain turn's own answer, and nothing after it
    assert plain.json()["session_title"] == "Out of rice"


def test_GIVEN_the_hub_WHEN_wired_THEN_per_turn_reflection_is_gone():
    assert not hasattr(pres_deps, "get_background_reflection_runner")
    assert not hasattr(pres_deps, "get_reflect_turn_use_case")


def test_GIVEN_the_hub_WHEN_wired_THEN_the_sweep_uses_the_settings_the_chat_model_and_the_hub_turns(monkeypatch):
    from app.core.config import settings

    monkeypatch.setattr(settings, "REFLECTION_IDLE_MINUTES", 3)
    sweep = di.get_container(session=None)[ReflectionSweepUseCase]

    assert sweep.idle.total_seconds() == 180
    assert sweep.residency is _ollama_connector
    assert sweep.turn_activity is pres_deps.get_session_lock_registry().turn_activity
    assert sweep.reflect_chat.proposal_repo is not None


def test_GIVEN_work_after_a_turn_WHEN_the_hub_asks_THEN_a_turn_counts_as_running(monkeypatch):
    activity = pres_deps.get_session_lock_registry().turn_activity
    assert not activity.any_running()

    monkeypatch.setattr(type(_after_turn_work), "running", property(lambda self: 1))

    assert activity.any_running()


def test_GIVEN_removing_a_memory_WHEN_wired_THEN_its_proposals_are_dropped_too():
    retire = di.get_container(session=None)[pres_deps.get_retire_memory_use_case]
    assert retire.proposal_repo is not None


@pytest.mark.asyncio
async def test_GIVEN_the_sweeper_WHEN_it_wakes_THEN_it_runs_a_pass_and_survives_a_failing_one(monkeypatch):
    passes = []

    class Sweep:
        async def run_pass(self):
            passes.append(1)
            if len(passes) == 1:
                raise RuntimeError("database locked")

    @asynccontextmanager
    async def fake_session():
        yield None

    monkeypatch.setattr(app_factory, "AsyncSessionLocal", fake_session)
    monkeypatch.setattr(app_factory, "get_container", lambda session: {ReflectionSweepUseCase: Sweep()})

    task = asyncio.create_task(app_factory.periodic_reflection_sweeper(interval_seconds=0.01))
    for _ in range(100):
        if len(passes) >= 2:
            break
        await asyncio.sleep(0.01)
    task.cancel()
    await asyncio.gather(task, return_exceptions=True)

    assert len(passes) >= 2


def test_GIVEN_no_override_THEN_reflection_starts_at_the_spec_defaults(monkeypatch):
    for name in ("REFLECTION_IDLE_MINUTES", "REFLECTION_SWEEP_SECONDS", "REFLECTION_NEIGHBOUR_BAR", "REFLECTION_NEIGHBOURS_MAX"):
        monkeypatch.delenv(name, raising=False)
    s = Settings(_env_file=None)
    assert (s.REFLECTION_IDLE_MINUTES, s.REFLECTION_SWEEP_SECONDS) == (10, 60)
    assert (s.REFLECTION_NEIGHBOUR_BAR, s.REFLECTION_NEIGHBOURS_MAX) == (0.45, 15)
    assert not hasattr(s, "MEMORY_REFLECTION_CONFIDENCE_THRESHOLD")
