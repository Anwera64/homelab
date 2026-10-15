"""Embedding on write and the filling pass (memory spec §4.3)."""

import json
import logging

import pytest

from app.domain.entities.memory import AgentMemory
from app.domain.entities.session import ConversationSession
from app.domain.entities.user import User
from app.domain.exceptions import EmbeddingException
from app.domain.use_cases.memories.create_memory import CreateMemoryUseCase
from app.domain.use_cases.memories.embed_memories import EmbedMemoriesUseCase
from app.domain.use_cases.memories.reflect_turn import ReflectTurnUseCase
from app.domain.use_cases.memories.update_memory import UpdateMemoryUseCase
from tests.domain.test_reflect_turn import (
    FakeLLMClient,
    FakeMemoryRepository as ReflectMemoryRepository,
    FakeSessionRepository as ReflectSessionRepository,
    FakeUnitOfWork,
    _house_resolver,
)


class FakeEmbedder:
    def __init__(self, fail: bool = False):
        self.fail = fail
        self.asked: list[list[str]] = []

    async def embed(self, texts):
        self.asked.append(list(texts))
        if self.fail:
            raise EmbeddingException("embedder is down")
        return [[float(len(t)), 1.0] for t in texts]


class FakeVectorRepository:
    def __init__(self, missing=None):
        self.saved: dict[tuple[str, str], tuple[str, list[float]]] = {}
        self._missing = list(missing or [])

    async def save(self, memory_id, kind, model, vector):
        self.saved[(memory_id, kind)] = (model, vector)

    async def missing(self, model):
        return list(self._missing)

    async def best_scores(self, query, memory_ids, model):
        return {}


class FakeMemoryRepository:
    def __init__(self, memories=()):
        self.memories = {m.id: m for m in memories}

    async def get_by_id(self, memory_id):
        return self.memories.get(memory_id)

    async def create(self, memory):
        self.memories[memory.id] = memory
        return memory

    async def update(self, memory):
        self.memories[memory.id] = memory
        return memory


def _embedder_use_case(embedder, vectors, memories=None):
    return EmbedMemoriesUseCase(
        embedder=embedder,
        vector_repo=vectors,
        memory_repo=memories or FakeMemoryRepository(),
        uow=FakeUnitOfWork(),
        model="bge-m3",
    )


@pytest.mark.asyncio
async def test_GIVEN_a_memory_with_relevant_for_WHEN_embedded_THEN_it_gets_both_vectors():
    embedder, vectors = FakeEmbedder(), FakeVectorRepository()
    memory = AgentMemory(id="veg", content="Sam is vegetarian", relevant_for=["dinner plans", "restaurants"])

    assert await _embedder_use_case(embedder, vectors).execute([memory]) == 1

    assert embedder.asked == [["Sam is vegetarian", "dinner plans; restaurants"]]
    assert vectors.saved[("veg", "content")] == ("bge-m3", [17.0, 1.0])
    assert vectors.saved[("veg", "relevant_for")][0] == "bge-m3"


@pytest.mark.asyncio
async def test_GIVEN_a_memory_without_relevant_for_THEN_only_its_content_is_embedded():
    embedder, vectors = FakeEmbedder(), FakeVectorRepository()

    await _embedder_use_case(embedder, vectors).execute([AgentMemory(id="m", content="Bins on Monday")])

    assert list(vectors.saved) == [("m", "content")]


@pytest.mark.asyncio
async def test_GIVEN_the_embedder_is_down_THEN_nothing_is_raised_and_the_failure_is_logged(caplog):
    vectors = FakeVectorRepository()

    with caplog.at_level(logging.WARNING):
        done = await _embedder_use_case(FakeEmbedder(fail=True), vectors).execute([AgentMemory(id="m", content="x")])

    assert done == 0
    assert vectors.saved == {}
    assert "Memory embed failed" in caplog.text and "memories=1" in caplog.text


@pytest.mark.asyncio
async def test_GIVEN_memories_without_vectors_WHEN_the_filling_pass_runs_THEN_only_those_are_embedded(caplog):
    stored = [AgentMemory(id="a", content="one"), AgentMemory(id="b", content="two")]
    embedder, vectors = FakeEmbedder(), FakeVectorRepository(missing=["b"])

    with caplog.at_level(logging.INFO):
        filled = await _embedder_use_case(embedder, vectors, FakeMemoryRepository(stored)).fill_missing()

    assert filled == 1
    assert embedder.asked == [["two"]]
    assert "Memory vectors filled | filled=1 missing=1" in caplog.text


@pytest.mark.asyncio
async def test_GIVEN_nothing_missing_THEN_the_filling_pass_asks_the_embedder_nothing():
    embedder = FakeEmbedder()

    assert await _embedder_use_case(embedder, FakeVectorRepository()).fill_missing() == 0
    assert embedder.asked == []


class _Sessions:
    async def get_by_id(self, session_id):
        return None


class _Agents:
    async def get_by_id(self, agent_id):
        return None


@pytest.mark.asyncio
async def test_GIVEN_a_member_adds_a_memory_THEN_it_is_embedded():
    embedder, vectors = FakeEmbedder(), FakeVectorRepository()
    memories = FakeMemoryRepository()
    use_case = CreateMemoryUseCase(
        memories, _Sessions(), _Agents(), FakeUnitOfWork(),
        embed_memories=_embedder_use_case(embedder, vectors, memories),
    )

    created = await use_case.execute(user_id="u1", content="Bins go out on Monday")

    assert (created.id, "content") in vectors.saved


@pytest.mark.asyncio
async def test_GIVEN_a_memory_is_reworded_THEN_it_is_embedded_again_but_not_when_only_confidence_changes():
    memory = AgentMemory(id="m", user_id="u1", content="Bins on Monday")
    memories = FakeMemoryRepository([memory])
    embedder, vectors = FakeEmbedder(), FakeVectorRepository()
    use_case = UpdateMemoryUseCase(
        memories, FakeUnitOfWork(), embed_memories=_embedder_use_case(embedder, vectors, memories)
    )
    me = User(id="u1", full_name="Emma", hashed_pin="h")

    await use_case.execute("m", me, confidence=0.5)
    assert embedder.asked == []

    await use_case.execute("m", me, content="Bins on Tuesday")
    assert embedder.asked == [["Bins on Tuesday"]]


@pytest.mark.asyncio
async def test_GIVEN_reflection_saves_memories_THEN_each_new_one_is_embedded_with_its_relevant_for():
    """And relevant_for comes from the model's reply (memory spec §5.2)."""
    reply = json.dumps({
        "memories": [
            {
                "category": "preference", "content": "Sam is vegetarian", "scope": "household",
                "confidence": 0.9, "relevant_for": ["dinner plans", "  ", "restaurants", 3],
            },
            {"category": "fact", "content": "Emma runs on Sundays", "scope": "personal", "confidence": 0.9,
             "relevant_for": "not a list"},
        ]
    })
    memories = ReflectMemoryRepository()
    embedder, vectors = FakeEmbedder(), FakeVectorRepository()
    use_case = ReflectTurnUseCase(
        llm_client=FakeLLMClient(reply),
        memory_repo=memories,
        session_repo=ReflectSessionRepository(ConversationSession(id="s1", user_id="u1", agent_id="a1")),
        uow=FakeUnitOfWork(),
        model_resolver=_house_resolver(),
        embed_memories=_embedder_use_case(embedder, vectors),
    )

    result = await use_case.execute(
        session_id="s1", user_id="u1", username="emma", agent_id="a1", agent_name="Planner",
        user_message="Sam is vegetarian, and I run on Sundays", assistant_message="Noted",
    )

    veg, run = result.memories_created
    assert veg.relevant_for == ["dinner plans", "restaurants"]
    assert run.relevant_for is None
    assert embedder.asked == [["Sam is vegetarian", "dinner plans; restaurants", "Emma runs on Sundays"]]
    assert {key[1] for key in vectors.saved} == {"content", "relevant_for"}


def test_reflection_asks_for_relevant_for():
    from app.domain.use_cases.memories.reflect_turn import EXTRACTION_SYSTEM_PROMPT

    assert '"relevant_for"' in EXTRACTION_SYSTEM_PROMPT
