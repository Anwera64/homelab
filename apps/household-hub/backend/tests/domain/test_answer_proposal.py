"""Answering a change to check, and undoing the answer (memory spec §6.1, §10 Replacement, Only the speaker answers)."""

import logging

import pytest

from app.domain.entities.memory_proposal import MemoryProposal
from app.domain.entities.session import ConversationSession
from app.domain.entities.user import User
from app.domain.exceptions import EntityNotFoundException, ProposalStateException, ZeroLeakViolationException
from app.domain.use_cases.memories.answer_proposal import (
    KEEP_BOTH,
    KEEP_STORED,
    USE_NEW,
    AnswerProposalUseCase,
    UndoProposalAnswerUseCase,
)
from tests.domain.reflection_fakes import (
    NOW,
    FakeEmbedMemories,
    FakeMemories,
    FakeProposals,
    FakeSessions,
    FakeUnitOfWork,
    memory,
)

EMMA = User(id="emma", full_name="Emma", is_admin=True)
LIAM = User(id="liam", full_name="Liam")


class World:
    """Liam said the boiler memory; Emma's chat with the Coordinator read a change to it."""

    def __init__(self, answerer="liam", state="pending", from_remember=False):
        self.stored = memory("boiler", "The boiler service is booked yearly in October.", user_id="liam", scope="household")
        self.stored.category = "milestone"
        self.memories = FakeMemories([self.stored])
        self.memories_to_add = []
        self.proposal = MemoryProposal(
            id="prop",
            memory_id="boiler",
            content="The boiler service moved to November this year.",
            relation="changed",
            answerer_id=answerer,
            source_session_id="chat",
            source_user_id="emma",
            state=state,
            created_at=NOW,
        )
        if from_remember:
            # Emma said "remember that the boiler moved": it waits for Liam, and nothing is saved yet.
            self.proposal.origin = "tool"
            self.proposal.expires_at = NOW.replace(year=NOW.year + 1)
        self.proposals = FakeProposals([self.proposal])
        self.sessions = FakeSessions(ConversationSession(id="chat", user_id="emma", agent_id="coordinator"), [])
        self.uow = FakeUnitOfWork()
        self.embed = FakeEmbedMemories()
        args = dict(
            proposal_repo=self.proposals,
            memory_repo=self.memories,
            uow=self.uow,
            clock=lambda: NOW,
        )
        self.answer = AnswerProposalUseCase(session_repo=self.sessions, embed_memories=self.embed, **args)
        self.undo = UndoProposalAnswerUseCase(**args)

    @property
    def new_memory(self):
        return next((m for m in self.memories.memories if m.id == self.proposal.new_memory_id), None)


@pytest.mark.asyncio
async def test_GIVEN_a_change_WHEN_keep_stored_THEN_it_is_closed_and_no_memory_is_saved():
    world = World()

    answered = await world.answer.execute("prop", KEEP_STORED, LIAM)

    assert answered.state == "kept" and answered.answered_at == NOW
    assert answered.new_memory_id is None
    assert [m.id for m in world.memories.memories] == ["boiler"]
    assert world.stored.is_active
    assert world.uow.commits == 1


@pytest.mark.asyncio
async def test_GIVEN_a_change_WHEN_keep_both_THEN_the_new_sentence_is_saved_beside_the_stored_one():
    world = World()

    answered = await world.answer.execute("prop", KEEP_BOTH, LIAM)

    assert answered.state == "both"
    new = world.new_memory
    assert new is not None and world.stored.is_active and new.is_active
    assert new.content == "The boiler service moved to November this year."
    # Said by whoever said it in the chat, with the stored memory's scope and category.
    assert (new.user_id, new.scope, new.category) == ("emma", "household", "milestone")
    assert (new.agent_id, new.source_session_id, new.origin) == ("coordinator", "chat", "reflection")
    assert world.embed.embedded == [new]


@pytest.mark.asyncio
async def test_GIVEN_a_change_WHEN_use_new_THEN_it_replaces_the_stored_one():
    world = World()

    answered = await world.answer.execute("prop", USE_NEW, LIAM)

    assert answered.state == "replaced"
    new = world.new_memory
    assert new is not None and new.is_active
    assert not world.stored.is_active
    assert world.stored.replaced_by_id == new.id and world.stored.retired_at == NOW


@pytest.mark.asyncio
async def test_GIVEN_someone_elses_change_WHEN_answered_THEN_it_is_refused_and_nothing_changes(caplog):
    world = World()

    with caplog.at_level(logging.INFO), pytest.raises(ZeroLeakViolationException):
        await world.answer.execute("prop", USE_NEW, EMMA)  # an admin, but not who said it

    assert world.proposal.state == "pending" and world.stored.is_active
    assert [m.id for m in world.memories.memories] == ["boiler"]
    assert "reason=not_theirs" in caplog.text


@pytest.mark.asyncio
@pytest.mark.parametrize("state", ["kept", "both", "replaced", "dropped"])
async def test_GIVEN_a_change_no_longer_pending_WHEN_answered_THEN_it_is_refused(state):
    world = World(state=state)

    with pytest.raises(ProposalStateException):
        await world.answer.execute("prop", KEEP_STORED, LIAM)


@pytest.mark.asyncio
async def test_GIVEN_an_unknown_change_or_answer_WHEN_answered_THEN_it_is_refused():
    world = World()

    with pytest.raises(EntityNotFoundException):
        await world.answer.execute("nope", KEEP_STORED, LIAM)
    with pytest.raises(ValueError):
        await world.answer.execute("prop", "maybe", LIAM)


@pytest.mark.asyncio
async def test_GIVEN_an_answer_WHEN_logged_THEN_it_has_ids_and_no_text_unless_asked(caplog):
    world = World()

    with caplog.at_level(logging.INFO):
        await world.answer.execute("prop", USE_NEW, LIAM)

    line = next(r.getMessage() for r in caplog.records if "Memory proposal answered" in r.getMessage())
    assert "member=liam" in line and "proposal=prop" in line and "memory=boiler" in line
    assert "answer=replaced" in line and f"new_memory={world.proposal.new_memory_id}" in line
    assert "November" not in caplog.text


@pytest.mark.asyncio
@pytest.mark.parametrize("answer", [KEEP_STORED, KEEP_BOTH, USE_NEW])
async def test_GIVEN_an_answer_WHEN_undone_THEN_everything_is_as_before(answer):
    world = World()
    await world.answer.execute("prop", answer, LIAM)

    undone = await world.undo.execute("prop", LIAM)

    assert undone.state == "pending" and undone.answered_at is None and undone.new_memory_id is None
    assert [m.id for m in world.memories.memories] == ["boiler"]
    assert world.stored.is_active
    assert world.stored.replaced_by_id is None and world.stored.retired_at is None


@pytest.mark.asyncio
async def test_GIVEN_someone_elses_answer_WHEN_undone_THEN_it_is_refused():
    world = World()
    await world.answer.execute("prop", USE_NEW, LIAM)

    with pytest.raises(ZeroLeakViolationException):
        await world.undo.execute("prop", EMMA)

    assert world.proposal.state == "replaced"


@pytest.mark.asyncio
@pytest.mark.parametrize("state", ["pending", "dropped"])
async def test_GIVEN_a_change_not_answered_WHEN_undone_THEN_it_is_refused(state):
    world = World(state=state)

    with pytest.raises(ProposalStateException):
        await world.undo.execute("prop", LIAM)


# --- A change raised by `remember` (memory spec §2.1): saved only once Liam answers --------------


@pytest.mark.asyncio
async def test_GIVEN_a_remembered_change_WHEN_keep_stored_THEN_nothing_of_emmas_is_saved():
    world = World(from_remember=True)

    answered = await world.answer.execute("prop", KEEP_STORED, LIAM)

    assert answered.state == "kept" and answered.new_memory_id is None
    assert world.stored.is_active
    assert [m.id for m in world.memories.memories] == ["boiler"] and world.embed.embedded == []


@pytest.mark.asyncio
@pytest.mark.parametrize("answer", [KEEP_BOTH, USE_NEW])
async def test_GIVEN_a_remembered_change_WHEN_kept_or_used_THEN_emmas_sentence_is_saved_as_she_asked(answer):
    world = World(from_remember=True)

    await world.answer.execute("prop", answer, LIAM)

    new = world.new_memory
    assert new is not None and new.is_active and world.embed.embedded == [new]
    assert (new.user_id, new.scope, new.content) == ("emma", "household", "The boiler service moved to November this year.")
    # Asked for in a chat, so it reads as "You asked" on Emma's page, with the end date she gave.
    assert (new.origin, new.agent_id, new.source_session_id) == ("tool", "coordinator", "chat")
    assert new.expires_at == world.proposal.expires_at
    assert world.stored.is_active is (answer == KEEP_BOTH)


@pytest.mark.asyncio
@pytest.mark.parametrize("answer", [KEEP_STORED, KEEP_BOTH, USE_NEW])
async def test_GIVEN_a_remembered_change_answered_WHEN_undone_THEN_it_waits_again_as_before(answer):
    world = World(from_remember=True)
    await world.answer.execute("prop", answer, LIAM)

    undone = await world.undo.execute("prop", LIAM)

    assert undone.state == "pending" and undone.answered_at is None and undone.new_memory_id is None
    assert [m.id for m in world.memories.memories] == ["boiler"]
    assert world.stored.is_active and world.stored.replaced_by_id is None and world.stored.retired_at is None
