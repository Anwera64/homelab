"""
Chats keep a rolling summary of older messages instead of resending them every turn.

`save_history_summary` writes only the summary and the message it stops at; nothing else about the
session should move — not the title, not is_secret, and not updated_at, which would otherwise
reorder the Chats list every time a summary is refreshed in the background. `get_messages_after`
is the other half: it hands back only what the summary does not already cover.
"""

from datetime import datetime, timedelta, timezone

import pytest
import pytest_asyncio
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from sqlalchemy.pool import StaticPool

from app.data.models.base import Base
from app.data.models.user_model import UserModel
from app.data.models.session_model import SessionModel, MessageModel

from app.data.datasources.session_data_source import SqliteSessionDataSource
from app.data.mappers.session_data_mapper import SessionDataMapper
from app.data.repositories.session_repository_impl import SessionRepositoryImpl
from app.domain.entities.session import ConversationSession


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
        yield sess

    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)


async def _make_user_and_session(sess: AsyncSession, session_id: str = "sess-1") -> None:
    sess.add(UserModel(id="user-1", full_name="Anwera", hashed_pin="hash"))
    sess.add(SessionModel(id=session_id, user_id="user-1", title="Original Title", is_secret=False))
    await sess.flush()


def _repo(sess: AsyncSession) -> SessionRepositoryImpl:
    return SessionRepositoryImpl(SqliteSessionDataSource(sess), SessionDataMapper())


_BASE_TIME = datetime(2026, 1, 1, tzinfo=timezone.utc)


def _add_messages(sess: AsyncSession, session_id: str, count: int) -> None:
    """Adds messages msg-0..msg-{count-1}, each a second apart, so ordering never depends on clock
    resolution."""
    for i in range(count):
        sess.add(
            MessageModel(
                id=f"msg-{i}",
                session_id=session_id,
                role="user",
                content=f"m{i}",
                created_at=_BASE_TIME + timedelta(seconds=i),
            )
        )


@pytest.mark.asyncio
async def test_GIVEN_a_session_WHEN_a_history_summary_is_saved_THEN_get_by_id_returns_both_fields(
    session: AsyncSession,
):
    await _make_user_and_session(session)
    repo = _repo(session)

    await repo.save_history_summary("sess-1", "the gist so far", "msg-42")
    await session.commit()

    fetched = await repo.get_by_id("sess-1")
    assert fetched is not None
    assert fetched.history_summary == "the gist so far"
    assert fetched.summarized_through_id == "msg-42"


@pytest.mark.asyncio
async def test_GIVEN_a_session_WHEN_a_history_summary_is_saved_THEN_title_is_secret_and_updated_at_are_unchanged(
    session: AsyncSession,
):
    await _make_user_and_session(session)
    repo = _repo(session)

    before = await repo.get_by_id("sess-1")
    assert before is not None
    original_updated_at = before.updated_at

    await repo.save_history_summary("sess-1", "the gist so far", "msg-42")
    await session.commit()

    after = await repo.get_by_id("sess-1")
    assert after is not None
    assert after.title == "Original Title"
    assert after.is_secret is False
    assert after.updated_at == original_updated_at


@pytest.mark.asyncio
async def test_GIVEN_a_saved_summary_WHEN_update_is_called_with_a_stale_summary_THEN_the_saved_summary_survives(
    session: AsyncSession,
):
    await _make_user_and_session(session)
    repo = _repo(session)

    await repo.save_history_summary("sess-1", "the gist so far", "msg-42")
    await session.commit()

    stale = await repo.get_by_id("sess-1")
    assert stale is not None
    stale.history_summary = None
    stale.summarized_through_id = None
    stale.title = "Renamed"

    await repo.update(stale)
    await session.commit()

    after = await repo.get_by_id("sess-1")
    assert after is not None
    assert after.title == "Renamed"
    assert after.history_summary == "the gist so far"
    assert after.summarized_through_id == "msg-42"


@pytest.mark.asyncio
async def test_GIVEN_no_after_id_WHEN_get_messages_after_is_called_THEN_all_messages_return_oldest_first(
    session: AsyncSession,
):
    await _make_user_and_session(session)
    repo = _repo(session)

    _add_messages(session, "sess-1", 3)
    await session.commit()

    messages = await repo.get_messages_after("sess-1", None)

    assert [m.id for m in messages] == ["msg-0", "msg-1", "msg-2"]


@pytest.mark.asyncio
async def test_GIVEN_an_after_id_WHEN_get_messages_after_is_called_THEN_only_later_messages_return(
    session: AsyncSession,
):
    await _make_user_and_session(session)
    repo = _repo(session)

    _add_messages(session, "sess-1", 4)
    await session.commit()

    messages = await repo.get_messages_after("sess-1", "msg-1")

    assert [m.id for m in messages] == ["msg-2", "msg-3"]


@pytest.mark.asyncio
async def test_GIVEN_an_unknown_after_id_WHEN_get_messages_after_is_called_THEN_all_messages_return(
    session: AsyncSession,
):
    await _make_user_and_session(session)
    repo = _repo(session)

    _add_messages(session, "sess-1", 2)
    await session.commit()

    messages = await repo.get_messages_after("sess-1", "does-not-exist")

    assert [m.id for m in messages] == ["msg-0", "msg-1"]


@pytest.mark.asyncio
async def test_GIVEN_more_messages_than_the_limit_WHEN_get_messages_after_is_called_THEN_the_newest_limit_return_oldest_first(
    session: AsyncSession,
):
    await _make_user_and_session(session)
    repo = _repo(session)

    _add_messages(session, "sess-1", 5)
    await session.commit()

    messages = await repo.get_messages_after("sess-1", None, limit=2)

    assert [m.id for m in messages] == ["msg-3", "msg-4"]
