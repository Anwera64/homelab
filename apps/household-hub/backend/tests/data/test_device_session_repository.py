from datetime import datetime, timedelta, timezone

import pytest
import pytest_asyncio
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from sqlalchemy.pool import StaticPool

from app.data.models.base import Base
from app.data.models.user_model import UserModel
from app.data.models.space_model import SpaceModel  # noqa: F401 - needed to resolve UserModel relationships
from app.data.models.agent_model import AgentModel  # noqa: F401
from app.data.models.session_model import SessionModel, MessageModel  # noqa: F401
from app.data.models.memory_model import MemoryModel  # noqa: F401
from app.data.models.device_session_model import DeviceSessionModel  # noqa: F401 - registers the table

from app.data.datasources.device_session_data_source import SqliteDeviceSessionDataSource
from app.data.mappers.device_session_data_mapper import DeviceSessionDataMapper
from app.data.repositories.device_session_repository_impl import DeviceSessionRepositoryImpl
from app.domain.entities.device_session import DeviceSession

EMMA = "member-emma"
LIAM = "member-liam"


@pytest_asyncio.fixture
async def session():
    engine = create_async_engine(
        "sqlite+aiosqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    session_factory = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)
    async with session_factory() as sess:
        sess.add(UserModel(id=EMMA, full_name="Emma", hashed_pin="hash", is_admin=True))
        sess.add(UserModel(id=LIAM, full_name="Liam", hashed_pin="hash"))
        await sess.commit()
        yield sess

    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)


def _repo(session: AsyncSession) -> DeviceSessionRepositoryImpl:
    return DeviceSessionRepositoryImpl(SqliteDeviceSessionDataSource(session), DeviceSessionDataMapper())


async def _signed_in(session: AsyncSession, user_id: str = EMMA, **overrides) -> DeviceSession:
    created = await _repo(session).create(DeviceSession(user_id=user_id, **overrides))
    await session.commit()
    return created


@pytest.mark.asyncio
async def test_GIVEN_a_device_signed_in_WHEN_it_is_looked_up_THEN_it_comes_back_with_utc_times(session: AsyncSession):
    created = await _signed_in(session)

    found = await _repo(session).get_by_id(created.id)

    assert found is not None
    assert found.user_id == EMMA
    assert found.created_at.tzinfo is not None
    assert found.last_seen_at.tzinfo is not None


@pytest.mark.asyncio
async def test_GIVEN_no_such_device_WHEN_it_is_looked_up_THEN_there_is_none(session: AsyncSession):
    assert await _repo(session).get_by_id("never-signed-in") is None


@pytest.mark.asyncio
async def test_GIVEN_a_device_signed_in_WHEN_it_is_touched_THEN_it_was_last_seen_then(session: AsyncSession):
    created = await _signed_in(session)
    later = created.last_seen_at + timedelta(days=3)

    await _repo(session).touch(created.id, later)
    await session.commit()

    assert (await _repo(session).get_by_id(created.id)).last_seen_at == later


@pytest.mark.asyncio
async def test_GIVEN_two_devices_WHEN_one_is_deleted_THEN_the_other_stays(session: AsyncSession):
    phone = await _signed_in(session)
    tablet = await _signed_in(session)

    await _repo(session).delete(phone.id)
    await session.commit()

    assert await _repo(session).get_by_id(phone.id) is None
    assert await _repo(session).get_by_id(tablet.id) is not None


@pytest.mark.asyncio
async def test_GIVEN_two_members_signed_in_WHEN_one_members_devices_are_deleted_THEN_the_others_stay(
    session: AsyncSession,
):
    emma_phone = await _signed_in(session, EMMA)
    emma_tablet = await _signed_in(session, EMMA)
    liam_phone = await _signed_in(session, LIAM)

    await _repo(session).delete_for_user(EMMA)
    await session.commit()

    assert await _repo(session).get_by_id(emma_phone.id) is None
    assert await _repo(session).get_by_id(emma_tablet.id) is None
    assert await _repo(session).get_by_id(liam_phone.id) is not None


@pytest.mark.asyncio
async def test_GIVEN_a_member_on_two_devices_WHEN_all_but_one_are_deleted_THEN_that_one_stays(session: AsyncSession):
    phone = await _signed_in(session)
    tablet = await _signed_in(session)

    await _repo(session).delete_for_user(EMMA, except_id=phone.id)
    await session.commit()

    assert await _repo(session).get_by_id(phone.id) is not None
    assert await _repo(session).get_by_id(tablet.id) is None


@pytest.mark.asyncio
async def test_GIVEN_an_old_and_a_recent_device_WHEN_those_last_seen_before_a_date_are_deleted_THEN_only_the_old_one_goes(
    session: AsyncSession,
):
    now = datetime.now(timezone.utc)
    old = await _signed_in(session, last_seen_at=now - timedelta(days=31))
    recent = await _signed_in(session, last_seen_at=now - timedelta(days=29))

    removed = await _repo(session).delete_last_seen_before(now - timedelta(days=30))
    await session.commit()

    assert removed == 1
    assert await _repo(session).get_by_id(old.id) is None
    assert await _repo(session).get_by_id(recent.id) is not None
