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
from app.data.models.invite_model import InviteModel  # noqa: F401 - registers the table

from app.data.datasources.invite_data_source import SqliteInviteDataSource
from app.data.mappers.invite_data_mapper import InviteDataMapper
from app.data.repositories.invite_repository_impl import InviteRepositoryImpl
from app.domain.entities.invite import Invite

INVITER_ID = "inviter-1"


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
        sess.add(UserModel(id=INVITER_ID, full_name="Inviter", hashed_pin="hash", is_admin=True))
        await sess.commit()
        yield sess

    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)


def _repo(session: AsyncSession) -> InviteRepositoryImpl:
    return InviteRepositoryImpl(SqliteInviteDataSource(session), InviteDataMapper())


def _invite(**overrides) -> Invite:
    now = datetime.now(timezone.utc)
    defaults = dict(
        code="K7M2QP",
        invited_name="Liam",
        inviter_id=INVITER_ID,
        expires_at=now + timedelta(minutes=15),
    )
    defaults.update(overrides)
    return Invite(**defaults)


@pytest.mark.asyncio
async def test_an_invite_is_found_by_its_code(session: AsyncSession):
    repo = _repo(session)
    created = await repo.create(_invite())
    await session.commit()

    found = await repo.get_by_code("K7M2QP")

    assert found is not None
    assert found.id == created.id
    assert found.invited_name == "Liam"
    assert found.inviter_id == INVITER_ID


@pytest.mark.asyncio
async def test_claiming_an_invite_succeeds_once(session: AsyncSession):
    repo = _repo(session)
    created = await repo.create(_invite())
    await session.commit()

    used_at = datetime.now(timezone.utc)
    first = await repo.claim(created.id, used_at)
    await session.commit()
    second = await repo.claim(created.id, datetime.now(timezone.utc))
    await session.commit()

    assert first is True
    assert second is False
    found = await repo.get_by_code("K7M2QP")
    assert found.used_at is not None


@pytest.mark.asyncio
async def test_retiring_a_name_removes_only_its_unused_invites(session: AsyncSession):
    repo = _repo(session)
    stale = await repo.create(_invite(code="AAAAAA", invited_name="liam"))
    await session.commit()
    used = await repo.create(_invite(code="BBBBBB", invited_name="LIAM"))
    await session.commit()
    await repo.claim(used.id, datetime.now(timezone.utc))
    await session.commit()
    other = await repo.create(_invite(code="CCCCCC", invited_name="Noor"))
    await session.commit()

    await repo.delete_unused_for_name("Liam")
    await session.commit()

    assert await repo.get_by_code("AAAAAA") is None
    assert await repo.get_by_code("BBBBBB") is not None
    assert await repo.get_by_code("CCCCCC") is not None


@pytest.mark.asyncio
async def test_datetimes_come_back_in_utc(session: AsyncSession):
    repo = _repo(session)
    created = await repo.create(_invite())
    await session.commit()
    await repo.claim(created.id, datetime.now(timezone.utc))
    await session.commit()

    found = await repo.get_by_code("K7M2QP")

    assert found.expires_at.tzinfo is not None
    assert found.created_at.tzinfo is not None
    assert found.used_at.tzinfo is not None
