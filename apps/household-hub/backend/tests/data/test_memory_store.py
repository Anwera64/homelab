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


@pytest.mark.asyncio
async def test_retiring_a_memory_is_kept_and_can_be_undone(session: AsyncSession):
    """GIVEN a memory WHEN it is retired and then restored THEN the row stays, inactive with retired_at, then active again."""
    repo = _repo(session)
    saved = await repo.create(AgentMemory(user_id="emma", content="Likes tea"))

    saved.is_active = False
    saved.retired_at = NOW
    await repo.update(saved)
    retired = await repo.get_by_id(saved.id)
    assert retired.is_active is False
    assert _utc(retired.retired_at) == NOW

    retired.is_active = True
    retired.retired_at = None
    await repo.update(retired)
    back = await repo.get_by_id(saved.id)
    assert back.is_active is True
    assert back.retired_at is None


@pytest.mark.asyncio
async def test_the_memory_page_lists_only_active_unexpired_memories(session: AsyncSession):
    """GIVEN personal and household memories, some retired or expired WHEN the page lists them THEN only current ones show, newest first."""
    session.add(UserModel(id="liam", full_name="Liam", hashed_pin="x"))
    await session.flush()
    repo = _repo(session)
    old = await repo.create(AgentMemory(user_id="emma", content="Old", created_at=NOW - timedelta(days=2)))
    new = await repo.create(AgentMemory(user_id="emma", content="New", created_at=NOW - timedelta(hours=1)))
    await repo.create(AgentMemory(user_id="emma", content="Retired", is_active=False, retired_at=NOW))
    await repo.create(AgentMemory(user_id="emma", content="Expired", expires_at=NOW - timedelta(minutes=1)))
    later = await repo.create(AgentMemory(user_id="emma", content="Until Friday", expires_at=NOW + timedelta(days=2)))
    await repo.create(AgentMemory(user_id="liam", content="Liam's own"))
    house = await repo.create(AgentMemory(user_id="liam", scope="household", content="Boiler in October"))
    mine_house = await repo.create(AgentMemory(user_id="emma", scope="household", content="Dinner Saturday"))
    await repo.create(
        AgentMemory(user_id="liam", scope="household", content="Gone", expires_at=NOW - timedelta(days=1))
    )

    personal = await repo.list_current_personal("emma", now=NOW)
    household = await repo.list_current_household(now=NOW)

    assert [m.id for m in personal] == [later.id, new.id, old.id]
    assert {m.id for m in household} == {house.id, mine_house.id}


@pytest.mark.asyncio
async def test_an_event_memory_keeps_its_start_and_how_its_time_is_shown(session: AsyncSession):
    """GIVEN a memory rebuilt from its event WHEN read back THEN its start and its time kind survive."""
    from app.domain.entities.memory import TIMED

    repo = _repo(session)
    start = datetime(2026, 10, 19, 17, 30, tzinfo=timezone.utc)
    created = await repo.create(
        AgentMemory(user_id="emma", content="Dinner together", event_id="ev1", event_starts_at=start, time_from_event=TIMED)
    )

    loaded = await repo.get_by_id(created.id)
    assert _utc(loaded.event_starts_at) == start
    assert loaded.time_from_event == TIMED


@pytest.mark.asyncio
async def test_updating_an_event_memory_saves_its_new_sentence_time_expiry_and_move(session: AsyncSession):
    """GIVEN an event memory WHEN it follows its event THEN update writes the event fields too."""
    from app.domain.entities.memory import TIMED

    repo = _repo(session)
    memory = await repo.create(AgentMemory(user_id="emma", content="Dinner on Saturday at 20:00", event_id="ev1"))
    start = datetime(2026, 10, 19, 17, 30, tzinfo=timezone.utc)
    memory.content, memory.event_starts_at, memory.time_from_event = "Dinner together", start, TIMED
    memory.moved_from = datetime(2026, 10, 18, 18, 0, tzinfo=timezone.utc)
    memory.expires_at = start + timedelta(hours=2)

    await repo.update(memory)

    loaded = await repo.get_by_id(memory.id)
    assert (loaded.content, _utc(loaded.event_starts_at), loaded.time_from_event) == ("Dinner together", start, TIMED)
    assert _utc(loaded.moved_from) == datetime(2026, 10, 18, 18, 0, tzinfo=timezone.utc)
    assert _utc(loaded.expires_at) == start + timedelta(hours=2)


MADRID_SUMMER = timezone(timedelta(hours=2))
MADRID_WINTER = timezone(timedelta(hours=1))


@pytest.mark.asyncio
async def test_GIVEN_event_times_in_the_calendars_own_zone_WHEN_a_memory_is_saved_THEN_they_come_back_as_the_same_moment_in_UTC(
    session: AsyncSession,
):
    repo = _repo(session)
    start = datetime(2026, 10, 24, 20, 0, tzinfo=MADRID_SUMMER)
    created = await repo.create(
        AgentMemory(
            user_id="emma",
            content="Dinner together",
            event_id="ev1",
            event_starts_at=start,
            expires_at=start + timedelta(hours=2),
            moved_from=datetime(2026, 10, 23, 20, 0, tzinfo=MADRID_SUMMER),
        )
    )

    loaded = await repo.get_by_id(created.id)

    assert loaded.event_starts_at == datetime(2026, 10, 24, 18, 0, tzinfo=timezone.utc)
    assert loaded.event_starts_at.tzinfo == timezone.utc
    assert loaded.expires_at == datetime(2026, 10, 24, 20, 0, tzinfo=timezone.utc)
    assert loaded.moved_from == datetime(2026, 10, 23, 18, 0, tzinfo=timezone.utc)


@pytest.mark.asyncio
async def test_GIVEN_an_event_memory_WHEN_it_follows_a_move_in_the_calendars_own_zone_THEN_the_new_times_are_saved_in_UTC(
    session: AsyncSession,
):
    repo = _repo(session)
    memory = await repo.create(AgentMemory(user_id="emma", content="Dinner together", event_id="ev1"))
    start = datetime(2026, 10, 25, 19, 30, tzinfo=MADRID_WINTER)
    memory.event_starts_at = start
    memory.expires_at = start + timedelta(hours=2)
    memory.moved_from = datetime(2026, 10, 24, 20, 0, tzinfo=MADRID_SUMMER)
    memory.retired_at = datetime(2026, 10, 20, 9, 0, tzinfo=MADRID_SUMMER)

    await repo.update(memory)

    loaded = await repo.get_by_id(memory.id)
    assert loaded.event_starts_at == datetime(2026, 10, 25, 18, 30, tzinfo=timezone.utc)
    assert loaded.event_starts_at.tzinfo == timezone.utc
    assert loaded.expires_at == datetime(2026, 10, 25, 20, 30, tzinfo=timezone.utc)
    assert loaded.moved_from == datetime(2026, 10, 24, 18, 0, tzinfo=timezone.utc)
    assert loaded.retired_at == datetime(2026, 10, 20, 7, 0, tzinfo=timezone.utc)


@pytest.mark.asyncio
async def test_memories_linked_to_an_event_are_found_by_member_and_event(session: AsyncSession):
    """GIVEN linked memories WHEN asked for one member's event THEN only that member's active ones come back."""
    session.add(UserModel(id="liam", full_name="Liam", hashed_pin="x"))
    await session.flush()
    repo = _repo(session)
    mine = await repo.create(AgentMemory(user_id="emma", content="Dinner", event_id="ev1"))
    await repo.create(AgentMemory(user_id="emma", content="Old dinner", event_id="ev1", is_active=False))
    await repo.create(AgentMemory(user_id="liam", content="His ev1", event_id="ev1"))
    await repo.create(AgentMemory(user_id="emma", content="Other", event_id="ev2"))

    assert [m.id for m in await repo.list_by_event("emma", "ev1")] == [mine.id]


@pytest.mark.asyncio
async def test_the_periodic_check_reads_only_active_unexpired_linked_memories(session: AsyncSession):
    """GIVEN linked, unlinked, expired and retired memories WHEN listed for the check THEN only the live links come back."""
    repo = _repo(session)
    live = await repo.create(AgentMemory(user_id="emma", content="Dinner", event_id="ev1", expires_at=NOW + timedelta(days=1)))
    forever = await repo.create(AgentMemory(user_id="emma", content="Yoga", event_id="ev2"))
    await repo.create(AgentMemory(user_id="emma", content="Past", event_id="ev3", expires_at=NOW - timedelta(days=1)))
    await repo.create(AgentMemory(user_id="emma", content="Retired", event_id="ev4", is_active=False))
    await repo.create(AgentMemory(user_id="emma", content="Plain fact"))

    assert {m.id for m in await repo.list_linked_current(NOW)} == {live.id, forever.id}
