"""
Each chat keeps a reflection marker (memory spec §3.4): the last message reflected, when, and what
that run did. The sweep asks for the chats with messages after their marker; `save_reflection`
writes the marker on its own, so a turn saving the session can't carry an old copy back over it.
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

_T0 = datetime(2026, 10, 15, 12, 0, tzinfo=timezone.utc)


@pytest_asyncio.fixture
async def session():
    engine = create_async_engine(
        "sqlite+aiosqlite:///:memory:", connect_args={"check_same_thread": False}, poolclass=StaticPool
    )
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    factory = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)
    async with factory() as sess:
        sess.add(UserModel(id="emma", full_name="Emma", hashed_pin="hash"))
        await sess.flush()
        yield sess
    await engine.dispose()


def _repo(sess: AsyncSession) -> SessionRepositoryImpl:
    return SessionRepositoryImpl(SqliteSessionDataSource(sess), SessionDataMapper())


def _chat(sess, chat_id, messages, archived=False):
    sess.add(SessionModel(id=chat_id, user_id="emma", title=chat_id, is_archived=archived))
    for i in range(messages):
        sess.add(
            MessageModel(
                id=f"{chat_id}-{i}",
                session_id=chat_id,
                role="user" if i % 2 == 0 else "assistant",
                content=f"m{i}",
                created_at=_T0 + timedelta(minutes=i),
            )
        )


@pytest.mark.asyncio
async def test_GIVEN_chats_WHEN_listed_for_reflection_THEN_only_those_with_messages_after_the_marker_come_back(session):
    _chat(session, "new", 3)
    _chat(session, "done", 2)
    _chat(session, "empty", 0)
    _chat(session, "archived", 2, archived=True)
    await session.flush()
    repo = _repo(session)
    await repo.save_reflection("done", "done-1", _T0, {"added": [], "proposed": [], "retired": []})

    awaiting = await repo.list_awaiting_reflection()

    assert [chat.session.id for chat in awaiting] == ["new"]
    assert awaiting[0].newest_message_at.replace(tzinfo=timezone.utc) == _T0 + timedelta(minutes=2)


@pytest.mark.asyncio
async def test_GIVEN_a_reflected_chat_WHEN_the_member_writes_again_THEN_it_awaits_reflection_again(session):
    _chat(session, "back", 2)
    await session.flush()
    repo = _repo(session)
    await repo.save_reflection("back", "back-1", _T0, {"added": [], "proposed": [], "retired": []})
    session.add(MessageModel(id="back-2", session_id="back", role="user", content="again", created_at=_T0 + timedelta(hours=1)))
    await session.flush()

    assert [chat.session.id for chat in await repo.list_awaiting_reflection()] == ["back"]


@pytest.mark.asyncio
async def test_GIVEN_a_saved_marker_WHEN_the_session_is_read_and_updated_THEN_the_marker_holds(session):
    _chat(session, "chat", 2)
    await session.flush()
    repo = _repo(session)
    before = await repo.get_by_id("chat")

    outcome = {"added": ["m1"], "proposed": ["p1"], "retired": [], "at": _T0.isoformat()}
    await repo.save_reflection("chat", "chat-1", _T0, outcome)
    before.title = "Renamed by a turn"
    await repo.update(before)

    after = await repo.get_by_id("chat")
    assert after.reflected_through_id == "chat-1"
    assert after.reflected_at.replace(tzinfo=timezone.utc) == _T0
    assert after.last_reflection == outcome
    assert after.title == "Renamed by a turn"
