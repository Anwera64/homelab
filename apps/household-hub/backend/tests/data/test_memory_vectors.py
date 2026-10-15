"""Memory vectors (memory spec §4): stored beside the memory, searched with sqlite-vec or in Python."""

import logging
import math

import pytest
import pytest_asyncio
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.pool import StaticPool

from app.data.datasources import sqlite_vec_extension
from app.data.datasources.memory_vector_data_source import SqliteMemoryVectorDataSource
from app.data.models.base import Base
from app.data.models.memory_model import MemoryModel
from app.data.models.user_model import UserModel
from app.data.repositories.memory_vector_repository_impl import MemoryVectorRepositoryImpl


@pytest_asyncio.fixture
async def session():
    engine = create_async_engine(
        "sqlite+aiosqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    factory = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)
    async with factory() as sess:
        sess.add(UserModel(id="emma", full_name="Emma", hashed_pin="x"))
        for memory_id, relevant_for in (("veg", ["dinner"]), ("run", None), ("old", None)):
            sess.add(
                MemoryModel(
                    id=memory_id, user_id="emma", content=memory_id, relevant_for=relevant_for,
                    is_active=memory_id != "old",
                )
            )
        await sess.flush()
        yield sess
    await engine.dispose()


def _repo(session: AsyncSession, use_extension: bool = True) -> MemoryVectorRepositoryImpl:
    return MemoryVectorRepositoryImpl(SqliteMemoryVectorDataSource(session, use_extension=use_extension))


def _unit(*values: float) -> list[float]:
    norm = math.sqrt(sum(v * v for v in values))
    return [v / norm for v in values]


@pytest.mark.asyncio
async def test_GIVEN_a_saved_vector_WHEN_saved_again_THEN_it_is_replaced(session):
    repo = _repo(session)
    await repo.save("veg", "content", "bge-m3", _unit(1, 0, 0))
    await repo.save("veg", "content", "bge-m3", _unit(0, 1, 0))

    scores = await repo.best_scores(_unit(0, 1, 0), ["veg"], "bge-m3")

    assert scores["veg"] == pytest.approx(1.0, abs=1e-5)
    count = (await session.execute(text("SELECT COUNT(*) FROM memory_vectors"))).scalar()
    assert count == 1


@pytest.mark.asyncio
async def test_GIVEN_vectors_from_another_model_WHEN_asked_what_is_missing_THEN_those_memories_count(session):
    """Active memories without a vector of this model for every kind they have; inactive ones wait."""
    repo = _repo(session)
    await repo.save("veg", "content", "bge-m3", _unit(1, 0))
    await repo.save("veg", "relevant_for", "bge-m3", _unit(0, 1))
    await repo.save("run", "content", "old-model", _unit(1, 0))

    assert await repo.missing("bge-m3") == ["run"]
    assert sorted(await repo.missing("new-model")) == ["run", "veg"]


@pytest.mark.asyncio
async def test_GIVEN_a_memory_with_content_but_no_relevant_for_vector_THEN_it_is_missing(session):
    repo = _repo(session)
    await repo.save("veg", "content", "bge-m3", _unit(1, 0))
    await repo.save("run", "content", "bge-m3", _unit(1, 0))

    assert await repo.missing("bge-m3") == ["veg"]


@pytest.mark.asyncio
@pytest.mark.parametrize("use_extension", [True, False])
async def test_GIVEN_two_vectors_WHEN_searched_THEN_a_memory_scores_by_the_better_one(session, use_extension):
    repo = _repo(session, use_extension)
    await repo.save("veg", "content", "bge-m3", _unit(1, 0, 0))
    await repo.save("veg", "relevant_for", "bge-m3", _unit(0, 1, 0))
    await repo.save("run", "content", "bge-m3", _unit(1, 1, 0))
    await repo.save("run", "relevant_for", "other-model", _unit(0, 1, 0))

    scores = await repo.best_scores(_unit(0, 1, 0), ["veg", "run"], "bge-m3")

    assert scores["veg"] == pytest.approx(1.0, abs=1e-5)
    assert scores["run"] == pytest.approx(math.sqrt(0.5), abs=1e-5)


@pytest.mark.asyncio
async def test_GIVEN_the_same_vectors_THEN_python_and_sqlite_vec_rank_alike(session):
    """Search fallback (§10): with the extension unavailable, search returns the same order."""
    ids = ["veg", "run", "old"]
    for index, memory_id in enumerate(ids):
        await _repo(session).save(memory_id, "content", "bge-m3", _unit(index + 1, 3 - index, 0.5))
    query = _unit(0.2, 1, 0.3)

    with_extension = await _repo(session, True).best_scores(query, ids, "bge-m3")
    in_python = await _repo(session, False).best_scores(query, ids, "bge-m3")

    assert sorted(with_extension, key=with_extension.get) == sorted(in_python, key=in_python.get)
    for memory_id in ids:
        assert with_extension[memory_id] == pytest.approx(in_python[memory_id], abs=1e-5)


@pytest.mark.asyncio
async def test_GIVEN_no_ids_WHEN_searched_THEN_nothing_is_asked(session):
    assert await _repo(session).best_scores(_unit(1, 0), [], "bge-m3") == {}


@pytest.mark.asyncio
async def test_GIVEN_a_connection_without_the_extension_THEN_search_falls_back_to_python_and_says_so(
    caplog, monkeypatch
):
    """A connection the extension never loaded on has no vec_distance_cosine; the same rows are ranked in Python."""
    monkeypatch.setattr(sqlite_vec_extension, "ENABLED", False)
    engine = create_async_engine("sqlite+aiosqlite:///:memory:", poolclass=StaticPool)
    try:
        async with engine.begin() as conn:
            await conn.run_sync(Base.metadata.create_all)
        factory = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)
        async with factory() as sess:
            sess.add(UserModel(id="emma", full_name="Emma", hashed_pin="x"))
            sess.add(MemoryModel(id="veg", user_id="emma", content="veg"))
            await sess.flush()
            repo = _repo(sess, use_extension=True)
            await repo.save("veg", "content", "bge-m3", _unit(1, 0))

            with caplog.at_level(logging.WARNING):
                scores = await repo.best_scores(_unit(1, 0), ["veg"], "bge-m3")

        assert scores["veg"] == pytest.approx(1.0, abs=1e-5)
        assert "ranking in Python" in caplog.text
    finally:
        await engine.dispose()


@pytest.mark.asyncio
async def test_GIVEN_any_hub_connection_THEN_sqlite_vec_is_loaded_and_SQL_cannot_load_extensions():
    engine = create_async_engine("sqlite+aiosqlite:///:memory:", poolclass=StaticPool)
    try:
        async with engine.connect() as conn:
            assert (await conn.execute(text("SELECT vec_version()"))).scalar().startswith("v")
            with pytest.raises(Exception, match="not authorized"):
                await conn.execute(text("SELECT load_extension('anything')"))
        assert sqlite_vec_extension.loaded() is True
    finally:
        await engine.dispose()
