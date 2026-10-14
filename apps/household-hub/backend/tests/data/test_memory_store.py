"""The one memory store (memory spec §3.1): what a memory keeps, and what the household reads."""

from datetime import datetime, timedelta, timezone

import pytest
import pytest_asyncio
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.pool import StaticPool

from app.data.datasources.memory_data_source import SqliteMemoryDataSource
from app.data.mappers.memory_data_mapper import MemoryDataMapper
from app.data.models.base import Base
from app.data.models.user_model import UserModel
from app.data.repositories.memory_repository_impl import MemoryRepositoryImpl
from app.domain.entities.memory import AgentMemory

NOW = datetime(2026, 10, 14, 12, 0, tzinfo=timezone.utc)


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
        await sess.flush()
        yield sess
    await engine.dispose()


def _repo(session: AsyncSession) -> MemoryRepositoryImpl:
    return MemoryRepositoryImpl(SqliteMemoryDataSource(session), MemoryDataMapper())


def _utc(value: datetime | None) -> datetime | None:
    # SQLite hands datetimes back without their zone; every one the hub writes is UTC.
    return value.replace(tzinfo=timezone.utc) if value and value.tzinfo is None else value


@pytest.mark.asyncio
async def test_a_memory_keeps_every_field_the_later_slices_need(session: AsyncSession):
    """GIVEN a memory with expiry, origin, relevance, an event, a replacement and a move WHEN read back THEN all of it survives."""
    repo = _repo(session)
    replacement = await repo.create(AgentMemory(user_id="emma", content="Dinner with Sam, Sunday 20:00"))
    saved = await repo.create(
        AgentMemory(
            user_id="emma",
            scope="household",
            content="Dinner with Sam, Saturday 20:00",
            expires_at=NOW + timedelta(days=3),
            origin="tool",
            relevant_for=["weekend plans", "Sam"],
            event_id="evt-123@caldav",
            replaced_by_id=replacement.id,
            retired_at=NOW,
            moved_from=NOW + timedelta(days=2),
        )
    )

    read = await repo.get_by_id(saved.id)

    assert _utc(read.expires_at) == NOW + timedelta(days=3)
    assert read.origin == "tool"
    assert read.relevant_for == ["weekend plans", "Sam"]
    assert read.event_id == "evt-123@caldav"
    assert read.replaced_by_id == replacement.id
    assert _utc(read.retired_at) == NOW
    assert _utc(read.moved_from) == NOW + timedelta(days=2)


@pytest.mark.asyncio
async def test_a_new_memory_comes_from_reflection_unless_it_says_otherwise(session: AsyncSession):
    """GIVEN a memory saved without an origin WHEN read back THEN its origin is reflection and nothing else is set."""
    repo = _repo(session)
    saved = await repo.create(AgentMemory(user_id="emma", content="Likes tea"))

    read = await repo.get_by_id(saved.id)

    assert read.origin == "reflection"
    assert read.expires_at is None
    assert read.relevant_for is None
    assert read.event_id is None
    assert read.retired_at is None
