"""
Finding a member's answers that mention something, without loading every message they have.

Connecting a calendar marks the calendar steps that failed for want of one as fixed (slice 4, PR 7).
Only answers whose saved parts name one of those failure reasons are worth reading, so the query
narrows by text first and the use case checks each part properly.
"""

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


def _failed(reason: str) -> dict:
    return {"parts": [{"type": "tool", "tool": "calendar_write", "success": False, "summary": {"reason": reason}}]}


@pytest.mark.asyncio
async def test_GIVEN_answers_across_members_WHEN_listing_those_mentioning_a_reason_THEN_only_the_members_answers_that_name_it_return(
    session: AsyncSession,
):
    session.add_all(
        [
            UserModel(id="emma", full_name="Emma", hashed_pin="hash"),
            UserModel(id="liam", full_name="Liam", hashed_pin="hash"),
            SessionModel(id="s-emma", user_id="emma", title="Emma's"),
            SessionModel(id="s-liam", user_id="liam", title="Liam's"),
        ]
    )
    await session.flush()
    session.add_all(
        [
            MessageModel(id="a-1", session_id="s-emma", role="assistant", content="", metadata_json=_failed("calendar_rejected")),
            MessageModel(id="a-2", session_id="s-emma", role="assistant", content="", metadata_json=_failed("blocked")),
            MessageModel(id="u-1", session_id="s-emma", role="user", content="calendar_rejected", metadata_json={}),
            MessageModel(id="a-3", session_id="s-liam", role="assistant", content="", metadata_json=_failed("calendar_rejected")),
            MessageModel(id="a-4", session_id="s-emma", role="assistant", content="", metadata_json=_failed("calendar_not_connected")),
        ]
    )
    await session.commit()
    repo = SessionRepositoryImpl(SqliteSessionDataSource(session), SessionDataMapper())

    found = await repo.list_assistant_messages_mentioning("emma", ["calendar_rejected", "calendar_not_connected"])

    assert sorted(m.id for m in found) == ["a-1", "a-4"]
