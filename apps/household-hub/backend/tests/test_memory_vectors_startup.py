"""The hub fills in missing memory vectors when it starts, which also loads the embedder (memory spec §4.3)."""

import pytest
from sqlalchemy import text

from app.bootstrap import app_factory
from app.bootstrap import di as di_module
from app.data.models.memory_model import MemoryModel
from app.domain.exceptions import EmbeddingException
from tests.conftest import TestingSessionLocal


class _Embedder:
    def __init__(self, fail=False):
        self.fail = fail

    async def embed(self, texts):
        if self.fail:
            raise EmbeddingException("down")
        return [[1.0, 0.0] for _ in texts]


async def _a_memory(db_session):
    from app.data.models.user_model import UserModel

    db_session.add(UserModel(id="emma", full_name="Emma", hashed_pin="x"))
    db_session.add(MemoryModel(id="veg", user_id="emma", content="Sam is vegetarian", relevant_for=["dinner"]))
    await db_session.commit()


@pytest.mark.asyncio
async def test_GIVEN_a_memory_without_vectors_WHEN_the_hub_starts_THEN_it_gets_them(db_session, monkeypatch):
    await _a_memory(db_session)
    monkeypatch.setattr(app_factory, "AsyncSessionLocal", TestingSessionLocal)
    monkeypatch.setattr(di_module, "_embedder", _Embedder())

    await app_factory.fill_memory_vectors()

    async with TestingSessionLocal() as session:
        kinds = (await session.execute(text("SELECT kind FROM memory_vectors ORDER BY kind"))).scalars().all()
    assert kinds == ["content", "relevant_for"]


@pytest.mark.asyncio
async def test_GIVEN_a_dead_embedder_WHEN_the_hub_starts_THEN_it_starts_anyway(db_session, monkeypatch):
    await _a_memory(db_session)
    monkeypatch.setattr(app_factory, "AsyncSessionLocal", TestingSessionLocal)
    monkeypatch.setattr(di_module, "_embedder", _Embedder(fail=True))

    await app_factory.fill_memory_vectors()
