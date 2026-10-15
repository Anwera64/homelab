"""
The sweep (memory spec §2.2): once a minute, each chat with messages after its marker is reflected
when it is idle, the model is loaded, no turn runs anywhere and no card waits. A turn always wins.
"""

import asyncio
import logging
from datetime import timedelta

import pytest

from app.bootstrap.turn_activity import HubTurnActivity
from app.domain.entities.agent import AgentPersonality
from app.domain.entities.llm_model import LLMModel
from app.domain.entities.session import PAUSED_TURN, ChatAwaitingReflection, ChatMessage, ConversationSession
from app.domain.use_cases.memories.reflect_chat import ReflectionResult
from app.domain.use_cases.memories.reflection_sweep import ReflectionSweepUseCase
from app.domain.use_cases.models.resolve_agent_model import ResolveAgentModelUseCase
from tests.domain.reflection_fakes import NOW


class Sessions:
    def __init__(self, chats, last_messages=None):
        self.chats = chats
        self.last = last_messages or {}
        self.saved = []

    async def list_awaiting_reflection(self):
        return [c for c in self.chats if c.session.reflected_through_id != self.last.get(c.session.id, ChatMessage(id="x")).id]

    async def get_messages(self, session_id, limit=50, before_id=None):
        last = self.last.get(session_id)
        return [last] if last else []

    async def save_reflection(self, session_id, through_id, at, outcome):
        self.saved.append((session_id, through_id, outcome))
        next(c for c in self.chats if c.session.id == session_id).session.reflected_through_id = through_id


class Agents:
    async def get_by_id(self, agent_id):
        return AgentPersonality(id=agent_id, name="Planner", llm_model_id="m1")


class Models:
    async def get_by_id(self, model_id):
        return LLMModel(id="m1", provider_model="qwen3.8-rvn", display_name="Qwen", is_default=True)

    async def get_default(self):
        return await self.get_by_id("m1")


class Residency:
    def __init__(self, until):
        self.until = until
        self.asked = []

    async def loaded_until(self, model):
        self.asked.append(model)
        return self.until


class Reflect:
    def __init__(self, sessions, block=False):
        self.sessions = sessions
        self.block = block
        self.runs = []
        self.started = asyncio.Event()

    async def execute(self, session, model, keep_alive):
        self.runs.append((session.id, model, keep_alive))
        self.started.set()
        if self.block:
            await asyncio.sleep(3600)
        last = self.sessions.last[session.id]
        await self.sessions.save_reflection(session.id, last.id, NOW, {"added": [], "proposed": [], "retired": []})
        return ReflectionResult(through_id=last.id, model_called=True)


class Fill:
    def __init__(self):
        self.calls = 0

    async def fill_missing(self):
        self.calls += 1
        return 0


def _chat(chat_id="chat", idle_minutes=15, secret=False, last=None):
    session = ConversationSession(id=chat_id, user_id="emma", agent_id="planner", is_secret=secret)
    last = last or ChatMessage(id=f"{chat_id}-last", session_id=chat_id, role="assistant", content="Done")
    return ChatAwaitingReflection(session=session, newest_message_at=NOW - timedelta(minutes=idle_minutes)), last


def _sweep(chats, until=NOW + timedelta(minutes=4, seconds=5), activity=None, block=False):
    sessions = Sessions([c for c, _ in chats], {c.session.id: last for c, last in chats})
    reflect = Reflect(sessions, block=block)
    fill = Fill()
    residency = Residency(until)
    activity = activity or HubTurnActivity()
    sweep = ReflectionSweepUseCase(
        session_repo=sessions,
        agent_repo=Agents(),
        model_resolver=ResolveAgentModelUseCase(Models()),
        residency=residency,
        turn_activity=activity,
        reflect_chat=reflect,
        embed_memories=fill,
        idle_minutes=10,
        clock=lambda: NOW,
    )
    return sweep, sessions, reflect, fill, residency


@pytest.mark.asyncio
async def test_GIVEN_an_idle_chat_and_a_loaded_model_WHEN_swept_THEN_it_is_reflected_with_the_time_left_on_the_model(caplog):
    sweep, sessions, reflect, fill, residency = _sweep([_chat()])

    with caplog.at_level(logging.INFO):
        await sweep.run_pass()

    assert reflect.runs == [("chat", "qwen3.8-rvn", 245)]
    assert residency.asked == ["qwen3.8-rvn"]
    assert "Reflection sweep | member=emma session=chat outcome=reflected" in caplog.text


@pytest.mark.asyncio
async def test_GIVEN_a_reflected_chat_WHEN_swept_again_THEN_nothing_runs():
    sweep, sessions, reflect, fill, _ = _sweep([_chat()])

    await sweep.run_pass()
    await sweep.run_pass()

    assert len(reflect.runs) == 1
    assert fill.calls == 2


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "case, reason",
    [
        ("young", "not_idle"),
        ("unloaded", "model_not_loaded"),
        ("expiring", "model_not_loaded"),
        ("turn", "turn_running"),
        ("card", "card_waiting"),
    ],
)
async def test_GIVEN_a_chat_that_fails_a_condition_WHEN_swept_THEN_it_is_skipped_and_the_reason_logged(case, reason, caplog):
    chat = _chat(idle_minutes=5 if case == "young" else 15)
    if case == "card":
        chat = _chat(
            last=ChatMessage(
                id="chat-last",
                session_id="chat",
                role="assistant",
                metadata_json={PAUSED_TURN: {}, "parts": [{"type": "proposal", "status": "pending"}]},
            )
        )
    until = {"unloaded": None, "expiring": NOW - timedelta(seconds=1)}.get(case, NOW + timedelta(minutes=5))
    activity = HubTurnActivity(after_turn_busy=lambda: case == "turn")
    sweep, sessions, reflect, fill, _ = _sweep([chat], until=until, activity=activity)

    with caplog.at_level(logging.INFO):
        await sweep.run_pass()

    assert reflect.runs == []
    assert sessions.saved == []
    assert f"session=chat outcome=skipped reason={reason}" in caplog.text


@pytest.mark.asyncio
async def test_GIVEN_a_secret_chat_WHEN_swept_THEN_nothing_is_read_and_the_marker_moves(caplog):
    sweep, sessions, reflect, fill, residency = _sweep([_chat(secret=True)])

    with caplog.at_level(logging.INFO):
        await sweep.run_pass()

    assert reflect.runs == []
    assert residency.asked == []
    assert sessions.saved == [("chat", "chat-last", {"added": [], "proposed": [], "retired": [], "at": NOW.isoformat()})]
    assert "outcome=skipped reason=secret" in caplog.text


@pytest.mark.asyncio
async def test_GIVEN_reflection_running_WHEN_a_turn_starts_THEN_it_is_cancelled_the_marker_stays_and_the_pass_ends(caplog):
    activity = HubTurnActivity()
    sweep, sessions, reflect, fill, _ = _sweep([_chat("one"), _chat("two")], activity=activity, block=True)

    with caplog.at_level(logging.INFO):
        sweeping = asyncio.create_task(sweep.run_pass())
        await reflect.started.wait()
        activity.turn_started()
        await asyncio.wait_for(sweeping, 1)

    assert [run[0] for run in reflect.runs] == ["one"]
    assert sessions.saved == []
    assert "session=one outcome=cancelled_by_turn" in caplog.text


@pytest.mark.asyncio
async def test_GIVEN_the_sweep_is_stopped_WHEN_reflecting_THEN_the_reflection_stops_too():
    sweep, sessions, reflect, fill, _ = _sweep([_chat()], block=True)

    sweeping = asyncio.create_task(sweep.run_pass())
    await reflect.started.wait()
    sweeping.cancel()

    with pytest.raises(asyncio.CancelledError):
        await sweeping


@pytest.mark.asyncio
async def test_GIVEN_a_reflection_that_fails_WHEN_swept_THEN_the_next_chat_still_runs(caplog):
    sweep, sessions, reflect, fill, _ = _sweep([_chat("bad"), _chat("good")])
    original = reflect.execute

    async def flaky(session, model, keep_alive):
        if session.id == "bad":
            raise RuntimeError("model said 500")
        return await original(session, model, keep_alive)

    reflect.execute = flaky

    with caplog.at_level(logging.INFO):
        await sweep.run_pass()

    assert [s[0] for s in sessions.saved] == ["good"]
    assert "session=bad outcome=failed" in caplog.text
