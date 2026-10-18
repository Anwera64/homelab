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


@pytest.mark.asyncio
async def test_GIVEN_a_proposal_WHEN_read_by_id_THEN_it_comes_back_and_an_unknown_id_is_none(session):
    repo = _repo(session)
    created = await repo.create(_proposal())

    found = await repo.get_by_id(created.id)

    assert found is not None
    assert (found.id, found.content, found.state, found.new_memory_id) == (
        created.id, "Boiler service in October", "pending", None
    )
    assert await repo.get_by_id("nope") is None


@pytest.mark.asyncio
async def test_GIVEN_an_answered_proposal_WHEN_updated_THEN_its_state_time_and_new_memory_are_stored(session):
    repo = _repo(session)
    proposal = await repo.create(_proposal())
    session.add(MemoryModel(id="new", user_id="emma", scope="household", content="Boiler service in October"))
    await session.flush()

    proposal.state = "replaced"
    proposal.answered_at = NOW
    proposal.new_memory_id = "new"
    await repo.update(proposal)

    row = (await session.execute(select(MemoryProposalModel))).scalars().one()
    assert (row.state, row.new_memory_id) == ("replaced", "new")
    assert row.answered_at is not None


@pytest.mark.asyncio
async def test_GIVEN_proposals_WHEN_listing_what_a_member_answers_THEN_only_their_pending_ones_newest_first(session):
    repo = _repo(session)
    older = _proposal(content="Boiler service in October")
    older.created_at = NOW.replace(hour=9)
    older = await repo.create(older)
    newer = await repo.create(_proposal(memory_id="other", content="Bins on Wednesday"))
    answered = _proposal(content="Boiler service in December")
    answered.state = "kept"
    await repo.create(answered)

    listed = await repo.list_pending_for_answerer("liam")

    assert [p.id for p in listed] == [newer.id, older.id]
    assert await repo.list_pending_for_answerer("emma") == []


@pytest.mark.asyncio
async def test_GIVEN_proposals_from_a_members_chats_WHEN_listing_THEN_only_pending_ones_others_answer(session):
    repo = _repo(session)
    for_liam = await repo.create(_proposal())
    own = _proposal(memory_id="other", content="Bins on Wednesday")
    own.answerer_id = "emma"
    await repo.create(own)
    dropped = _proposal(content="Boiler service in December")
    dropped.state = "dropped"
    await repo.create(dropped)

    listed = await repo.list_pending_from_user("emma")

    assert [p.id for p in listed] == [for_liam.id]
    assert await repo.list_pending_from_user("liam") == []


@pytest.mark.asyncio
async def test_GIVEN_proposals_WHEN_read_by_ids_THEN_the_known_ones_come_back(session):
    repo = _repo(session)
    one = await repo.create(_proposal())
    two = await repo.create(_proposal(memory_id="other", content="Bins on Wednesday"))

    found = await repo.get_many([two.id, "nope", one.id])

    assert {p.id for p in found} == {one.id, two.id}
    assert await repo.get_many([]) == []


@pytest.mark.asyncio
async def test_a_proposal_from_remember_keeps_where_it_came_from_and_its_end_date(session):
    proposal = _proposal()
    proposal.origin = "tool"
    proposal.expires_at = datetime(2026, 12, 1, tzinfo=timezone.utc)

    await _repo(session).create(proposal)

    (read,) = await _repo(session).list_pending_for_answerer("liam")
    assert read.origin == "tool" and read.new_memory_id is None
    assert read.expires_at.replace(tzinfo=timezone.utc) == datetime(2026, 12, 1, tzinfo=timezone.utc)
    assert (await _repo(session).create(_proposal(content="x"))).origin == "reflection"
