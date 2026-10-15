"""Proposals (memory spec §3.5): a pending change to a stored memory, kept until answered or dropped."""

from datetime import datetime, timezone

import pytest
import pytest_asyncio
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.pool import StaticPool

from app.data.datasources.memory_proposal_data_source import SqliteMemoryProposalDataSource
from app.data.mappers.memory_proposal_data_mapper import MemoryProposalDataMapper
from app.data.models.base import Base
from app.data.models.memory_model import MemoryModel
from app.data.models.memory_proposal_model import MemoryProposalModel
from app.data.models.session_model import SessionModel
from app.data.models.user_model import UserModel
from app.data.repositories.memory_proposal_repository_impl import MemoryProposalRepositoryImpl
from app.domain.entities.memory_proposal import MemoryProposal

NOW = datetime(2026, 10, 15, 12, 0, tzinfo=timezone.utc)


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
        sess.add(UserModel(id="liam", full_name="Liam", hashed_pin="x"))
        sess.add(SessionModel(id="chat", user_id="emma", title="Boiler"))
        sess.add(MemoryModel(id="boiler", user_id="liam", scope="household", content="Boiler service in November"))
        sess.add(MemoryModel(id="other", user_id="liam", scope="household", content="Bins on Tuesday"))
        await sess.flush()
        yield sess
    await engine.dispose()


def _repo(session: AsyncSession) -> MemoryProposalRepositoryImpl:
    return MemoryProposalRepositoryImpl(SqliteMemoryProposalDataSource(session), MemoryProposalDataMapper())


def _proposal(memory_id="boiler", content="Boiler service in October") -> MemoryProposal:
    return MemoryProposal(
        memory_id=memory_id,
        content=content,
        relation="changed",
        answerer_id="liam",
        source_session_id="chat",
        source_user_id="emma",
        created_at=NOW,
    )


@pytest.mark.asyncio
async def test_GIVEN_a_proposal_WHEN_created_THEN_every_field_is_stored_and_it_is_pending(session):
    created = await _repo(session).create(_proposal())

    row = (await session.execute(select(MemoryProposalModel))).scalars().one()
    assert row.id == created.id
    assert (row.memory_id, row.content, row.relation) == ("boiler", "Boiler service in October", "changed")
    assert (row.answerer_id, row.source_session_id, row.source_user_id) == ("liam", "chat", "emma")
    assert row.state == "pending" and created.state == "pending"
    assert row.answered_at is None


@pytest.mark.asyncio
async def test_GIVEN_pending_and_answered_proposals_WHEN_dropped_for_a_memory_THEN_only_its_pending_ones_go(session):
    repo = _repo(session)
    pending = await repo.create(_proposal())
    answered = _proposal(content="Boiler service in December")
    answered.state = "kept"
    answered = await repo.create(answered)
    elsewhere = await repo.create(_proposal(memory_id="other", content="Bins on Wednesday"))

    dropped = await repo.drop_pending_for("boiler")

    assert dropped == [pending.id]
    states = {row.id: row.state for row in (await session.execute(select(MemoryProposalModel))).scalars()}
    assert states == {pending.id: "dropped", answered.id: "kept", elsewhere.id: "pending"}
