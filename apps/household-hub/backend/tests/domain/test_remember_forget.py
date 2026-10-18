"""The `remember` and `forget` tools once they run (memory spec §2.1, §10 `remember`, `remember` on another's memory)."""

import logging

import pytest

from app.domain.entities.user import User
from app.domain.exceptions import EntityNotFoundException, InvalidOperationException, ZeroLeakViolationException
from app.domain.use_cases.memories.match_memory import MatchMemoryUseCase
from app.domain.use_cases.memories.memory_block import BuildMemoryBlockUseCase
from app.domain.use_cases.memories.remember_forget import ForgetUseCase, RememberUseCase
from app.domain.use_cases.memories.retire_memory import RetireMemoryUseCase
from tests.domain.reflection_fakes import (
    NOW,
    FakeEmbedder,
    FakeEmbedMemories,
    FakeMemories,
    FakeProposals,
    FakeUnitOfWork,
    FakeUsers,
    FakeVectors,
    memory,
)

EMMA = User(id="emma", full_name="Emma")
LIAM = User(id="liam", full_name="Liam")


class World:
    """Emma said Sam is vegetarian and has a jury date; Liam said when the boiler is serviced."""

    def __init__(self, scores=None, log_text=False):
        self.vegetarian = memory("veg", "Sam is vegetarian and doesn't eat fish.", user_id="emma", scope="household")
        self.jury = memory("jury", "Studio Jury on Friday 17 October, 09:00.", user_id="emma")
        self.boiler = memory("boiler", "The boiler service is booked yearly in October.", user_id="liam", scope="household")
        self.memories = FakeMemories([self.vegetarian, self.jury, self.boiler])
        self.proposals = FakeProposals()
        self.uow = FakeUnitOfWork()
        self.embed = FakeEmbedMemories()
        users = FakeUsers([EMMA, LIAM])
        match = MatchMemoryUseCase(
            memory_block=BuildMemoryBlockUseCase(self.memories, user_repo=users, clock=lambda: NOW),
            embedder=FakeEmbedder(),
            vector_repo=FakeVectors(scores or {}),
            model="bge-m3",
        )
        self.remember = RememberUseCase(
            memory_repo=self.memories,
            proposal_repo=self.proposals,
            match=match,
            user_repo=users,
            uow=self.uow,
            embed_memories=self.embed,
            clock=lambda: NOW,
            log_text=log_text,
        )
        retire = RetireMemoryUseCase(self.memories, self.uow, clock=lambda: NOW, proposal_repo=self.proposals)
        self.forget = ForgetUseCase(match=match, retire=retire, user_repo=users)

    def new(self, memory_id):
        return next(m for m in self.memories.memories if m.id == memory_id)


@pytest.mark.asyncio
async def test_GIVEN_nothing_it_replaces_THEN_a_new_memory_is_saved_from_the_chat():
    world = World()

    done = await world.remember.execute(
        EMMA, "Sam's birthday is 3 March.", scope="household", category="milestone", session_id="chat", agent_id="planner"
    )

    saved = world.new(done.memory.id)
    assert (saved.user_id, saved.scope, saved.category, saved.content) == ("emma", "household", "milestone", "Sam's birthday is 3 March.")
    assert (saved.origin, saved.source_session_id, saved.agent_id) == ("tool", "chat", "planner")
    assert done.replaced is None and done.kept_beside is None
    assert world.uow.commits == 1
    assert world.embed.embedded == [saved]


@pytest.mark.asyncio
async def test_unknown_scope_and_category_fall_back_and_blank_text_is_refused():
    world = World()

    done = await world.remember.execute(EMMA, "Emma likes tea.", scope="everyone", category="vibes")

    assert (done.memory.scope, done.memory.category) == ("personal", "fact")
    with pytest.raises(InvalidOperationException):
        await world.remember.execute(EMMA, "   ")


@pytest.mark.asyncio
async def test_GIVEN_it_replaces_the_members_own_memory_THEN_the_old_one_is_retired_in_its_favour():
    world = World(scores={"veg": 0.83})

    done = await world.remember.execute(
        EMMA, "Sam is vegan now.", scope="household", replaces="Sam is vegetarian", session_id="chat"
    )

    assert done.replaced is world.vegetarian
    assert not world.vegetarian.is_active
    assert world.vegetarian.replaced_by_id == done.memory.id
    assert world.vegetarian.retired_at == NOW
    assert world.proposals.dropped_for == ["veg"]
    assert world.proposals.created == []


@pytest.mark.asyncio
async def test_GIVEN_the_memory_a_card_showed_THEN_that_one_is_replaced_whatever_the_quote():
    world = World()

    done = await world.remember.execute(EMMA, "The jury moved to Monday.", replaces="something else", memory_id="jury")

    assert done.replaced is world.jury


@pytest.mark.asyncio
async def test_GIVEN_the_quote_matches_nothing_THEN_it_is_saved_as_new_and_nothing_retires():
    world = World(scores={"veg": 0.4})

    done = await world.remember.execute(EMMA, "Sam is vegan now.", replaces="Sam eats no meat")

    assert done.replaced is None
    assert world.vegetarian.is_active


@pytest.mark.asyncio
async def test_GIVEN_liams_memory_THEN_his_stays_mine_is_household_and_he_is_asked(caplog):
    world = World(scores={"boiler": 0.9})

    with caplog.at_level(logging.INFO):
        done = await world.remember.execute(
            EMMA, "The boiler service moved to November.", scope="personal", replaces="boiler in October", session_id="chat"
        )

    assert world.boiler.is_active
    assert done.memory.scope == "household" and done.memory.user_id == "emma"
    assert done.kept_beside is world.boiler and done.speaker_name == "Liam"
    (proposal,) = world.proposals.created
    assert (proposal.memory_id, proposal.answerer_id, proposal.source_user_id) == ("boiler", "liam", "emma")
    assert (proposal.content, proposal.source_session_id) == ("The boiler service moved to November.", "chat")
    assert proposal.origin == "tool" and proposal.new_memory_id == done.memory.id
    assert world.proposals.dropped_for == ["boiler"]
    assert "outcome=kept_beside" in caplog.text and "matched=boiler" in caplog.text
    assert "November" not in caplog.text


@pytest.mark.asyncio
async def test_GIVEN_the_text_setting_on_THEN_the_log_carries_the_sentence(caplog):
    world = World(log_text=True)

    with caplog.at_level(logging.INFO):
        await world.remember.execute(EMMA, "Emma likes tea.")

    assert "text='Emma likes tea.'" in caplog.text


@pytest.mark.asyncio
async def test_forget_retires_the_members_own_memory():
    world = World()

    forgotten = await world.forget.execute(EMMA, "Studio Jury on Friday 17 October, 09:00.")

    assert forgotten is world.jury
    assert not world.jury.is_active and world.jury.retired_at == NOW


@pytest.mark.asyncio
async def test_forget_of_liams_memory_is_refused_by_name_and_nothing_changes():
    world = World(scores={"boiler": 0.9})

    with pytest.raises(ZeroLeakViolationException, match="Only Liam can remove that memory"):
        await world.forget.execute(EMMA, "the boiler thing")

    assert world.boiler.is_active


@pytest.mark.asyncio
async def test_forget_of_something_not_stored_says_so():
    world = World()

    with pytest.raises(EntityNotFoundException, match="Nothing was forgotten"):
        await world.forget.execute(EMMA, "Sam's dentist")


# --- What a card shows ---------------------------------------------------------------------------


class FakeAgents:
    async def get_by_id(self, agent_id):
        from app.domain.entities.agent import AgentPersonality

        return AgentPersonality(id=agent_id, name="Planner") if agent_id == "planner" else None


def _card(world):
    from app.domain.use_cases.memories.remember_forget import MemoryOnCardUseCase

    return MemoryOnCardUseCase(match=world.forget.match, forget=world.forget, user_repo=FakeUsers([EMMA, LIAM]), agent_repo=FakeAgents())


@pytest.mark.asyncio
async def test_a_remember_card_shows_liams_memory_with_his_name_and_pins_it():
    world = World(scores={"boiler": 0.9})
    world.boiler.agent_id = "planner"

    shown, pinned, refusal = await _card(world).execute(EMMA, "remember", {"content": "x", "replaces": "the boiler"})

    assert pinned == {"memory_id": "boiler"}
    assert shown == {
        "stored": {
            "content": "The boiler service is booked yearly in October.",
            "said_by": "Liam",
            "mine": False,
            "agent": "Planner",
            "created_at": world.boiler.created_at.isoformat(),
        }
    }
    assert refusal is None


@pytest.mark.asyncio
async def test_a_remember_card_that_replaces_nothing_shows_nothing_extra():
    world = World(scores={"veg": 0.2})

    assert await _card(world).execute(EMMA, "remember", {"content": "x", "replaces": "dogs"}) == ({}, {}, None)
    assert await _card(world).execute(EMMA, "remember", {"content": "x"}) == ({}, {}, None)


@pytest.mark.asyncio
async def test_a_forget_card_shows_the_members_own_memory():
    world = World()

    shown, pinned, refusal = await _card(world).execute(EMMA, "forget", {"memory": "Studio Jury on Friday 17 October, 09:00."})

    assert pinned == {"memory_id": "jury"}
    assert shown["stored"]["mine"] is True and shown["stored"]["said_by"] == "Emma"
    assert shown["stored"]["agent"] is None
    assert refusal is None


@pytest.mark.asyncio
async def test_a_forget_with_no_card_to_show_says_why():
    world = World(scores={"boiler": 0.9})

    _, _, refusal = await _card(world).execute(EMMA, "forget", {"memory": "the boiler"})
    assert refusal == "Only Liam can remove that memory. Nothing was forgotten."

    _, _, refusal = await _card(World()).execute(EMMA, "forget", {"memory": "the dentist"})
    assert refusal == "No stored memory matches that. Nothing was forgotten."


@pytest.mark.asyncio
async def test_other_tools_have_nothing_to_look_up():
    assert await _card(World()).execute(EMMA, "calendar_write", {"title": "x"}) == ({}, {}, None)
