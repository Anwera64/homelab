import pytest
import pytest_asyncio
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.pool import StaticPool

from app.data.datasources.calendar_source_data_source import SqliteCalendarSourceDataSource
from app.data.datasources.memory_data_source import SqliteMemoryDataSource
from app.data.mappers.calendar_source_data_mapper import CalendarSourceDataMapper
from app.data.mappers.memory_data_mapper import MemoryDataMapper
from app.data.models.base import Base
from app.data.models.calendar_credential_model import CalendarCredentialModel
from app.data.models.user_model import UserModel
from app.data.repositories.calendar_source_repository_impl import CalendarSourceRepositoryImpl
from app.data.repositories.memory_repository_impl import MemoryRepositoryImpl
from app.domain.entities.calendar_source import CalendarSource
from app.domain.entities.memory import AgentMemory

HOME = CalendarSource(key="c1", url="https://cal.example/home/", name="Home", writable=True, picked=True)
WORK = CalendarSource(key="c2", url="https://cal.example/work/", name="Work", writable=True, picked=False)
HOLIDAYS = CalendarSource(key="c3", url="https://cal.example/holidays/", name="Holidays", writable=False, picked=False)


@pytest_asyncio.fixture
async def session():
    engine = create_async_engine(
        "sqlite+aiosqlite:///:memory:", connect_args={"check_same_thread": False}, poolclass=StaticPool
    )
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    factory = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)
    async with factory() as sess:
        sess.add(UserModel(id="emma", full_name="Emma", hashed_pin="x"))
        await sess.flush()
        sess.add(CalendarCredentialModel(
            id="cred-1", user_id="emma", provider="apple_icloud", url="https://cal.example",
            username="emma", encrypted_secret="enc",
        ))
        await sess.flush()
        yield sess
    await engine.dispose()


def _repo(session: AsyncSession) -> CalendarSourceRepositoryImpl:
    return CalendarSourceRepositoryImpl(SqliteCalendarSourceDataSource(session), CalendarSourceDataMapper())


@pytest.mark.asyncio
async def test_a_members_calendars_come_back_as_saved_in_the_accounts_order(session):
    repo = _repo(session)

    await repo.replace_all("cred-1", [WORK, HOME, HOLIDAYS])

    assert await repo.list_for("cred-1") == [WORK, HOME, HOLIDAYS]


@pytest.mark.asyncio
async def test_saving_the_calendars_again_replaces_them(session):
    repo = _repo(session)
    await repo.replace_all("cred-1", [HOME, WORK, HOLIDAYS])

    renamed = CalendarSource(key="c1", url=HOME.url, name="Family", writable=True, picked=False)
    await repo.replace_all("cred-1", [renamed])

    assert await repo.list_for("cred-1") == [renamed]


@pytest.mark.asyncio
async def test_a_connection_with_no_calendars_kept_yet_has_none(session):
    assert await _repo(session).list_for("cred-1") == []


@pytest.mark.asyncio
async def test_an_event_memory_keeps_the_calendar_its_event_is_on(session):
    memories = MemoryRepositoryImpl(SqliteMemoryDataSource(session), MemoryDataMapper())
    created = await memories.create(AgentMemory(user_id="emma", content="Dinner", event_id="ev1", event_calendar="c2"))

    created.event_calendar = "c3"
    await memories.update(created)

    assert (await memories.get_by_id(created.id)).event_calendar == "c3"
