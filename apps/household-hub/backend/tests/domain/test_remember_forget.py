"""The `remember` and `forget` tools once they run (memory spec §2.1, §10 `remember`, `remember` on another's memory)."""

import logging

import pytest

from app.domain.entities.user import User
from app.domain.exceptions import (
    EntityNotFoundException,
    InvalidOperationException,
    ProposalStateException,
    ZeroLeakViolationException,
)
from app.domain.use_cases.memories.match_memory import MatchMemoryUseCase
from app.domain.use_cases.memories.memory_block import BuildMemoryBlockUseCase
from app.domain.use_cases.memories.remember_forget import ForgetUseCase, RememberUseCase
from app.domain.use_cases.memories.retire_memory import RetireMemoryUseCase
from app.domain.use_cases.memories.withdraw_change import WithdrawChangeUseCase
from app.domain.entities.session import ConversationSession
from tests.domain.reflection_fakes import (
    NOW,
    FakeEmbedder,
    FakeEmbedMemories,
    FakeMemories,
    FakeProposals,
    FakeSessions,
    FakeUnitOfWork,
    FakeUsers,
    FakeVectors,
    memory,
)

EMMA = User(id="emma", full_name="Emma")
LIAM = User(id="liam", full_name="Liam")


class World:
    """Emma said Sam is vegetarian and has a jury date; Liam said when the boiler is serviced."""

    def __init__(self, scores=None, log_text=False, embedder=None):
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
            embedder=embedder or FakeEmbedder(),
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
        withdraw = WithdrawChangeUseCase(proposal_repo=self.proposals, memory_repo=self.memories, uow=self.uow)
        self.forget = ForgetUseCase(
            match=match, retire=retire, user_repo=users, proposal_repo=self.proposals, withdraw=withdraw
        )

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
async def test_GIVEN_liams_memory_THEN_mine_waits_for_him_and_nothing_is_saved_or_used_yet(caplog):
    world = World(scores={"boiler": 0.9})
    before = list(world.memories.memories)

    with caplog.at_level(logging.INFO):
        done = await world.remember.execute(
            EMMA, "The boiler service moved to November.", scope="personal", replaces="boiler in October",
            session_id="chat", agent_id="planner", expires_at="2026-12-01T00:00:00Z",
        )

    # Agents only read stored memories: until Liam answers there is nothing new to read.
    assert world.memories.memories == before and world.embed.embedded == []
    assert world.boiler.is_active
    assert done.memory is None and done.kept_beside is world.boiler and done.speaker_name == "Liam"
    (proposal,) = world.proposals.created
    assert done.held is proposal
    assert (proposal.memory_id, proposal.answerer_id, proposal.source_user_id) == ("boiler", "liam", "emma")
    assert (proposal.content, proposal.source_session_id) == ("The boiler service moved to November.", "chat")
    assert (proposal.origin, proposal.new_memory_id, proposal.state) == ("tool", None, "pending")
    assert proposal.expires_at.isoformat() == "2026-12-01T00:00:00+00:00"
    # Liam's memory hasn't changed, so the changes already waiting on it stay.
    assert world.proposals.dropped_for == []
    assert "outcome=held" in caplog.text and "matched=boiler" in caplog.text
    assert "November" not in caplog.text


@pytest.mark.asyncio
async def test_GIVEN_mine_already_waits_on_liams_memory_WHEN_remembered_again_THEN_the_waiting_one_is_replaced(caplog):
    world = World(scores={"boiler": 0.9})
    first = await world.remember.execute(EMMA, "The boiler service moved to November.", replaces="boiler")

    with caplog.at_level(logging.INFO):
        again = await world.remember.execute(EMMA, "The boiler service moved to December.", replaces="boiler", session_id="later")

    (proposal,) = world.proposals.created
    assert again.held is first.held is proposal
    assert (proposal.content, proposal.source_session_id) == ("The boiler service moved to December.", "later")
    assert "outcome=held_replaced" in caplog.text


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


# --- When Liam's memory goes while Emma's sentence waits on it -----------------------------------


@pytest.mark.asyncio
async def test_GIVEN_emmas_sentence_waits_on_liams_memory_WHEN_he_removes_his_THEN_hers_is_saved(caplog):
    from app.domain.entities.memory_proposal import MemoryProposal

    world = World(scores={"boiler": 0.9})
    done = await world.remember.execute(EMMA, "The boiler service moved to November.", replaces="boiler", session_id="chat")
    reflected = await world.proposals.create(MemoryProposal(memory_id="boiler", content="Booked for spring.", answerer_id="liam"))
    retire = RetireMemoryUseCase(
        world.memories, world.uow, clock=lambda: NOW, proposal_repo=world.proposals,
        session_repo=FakeSessions(ConversationSession(id="chat", user_id="emma", agent_id="planner"), []),
        embed_memories=world.embed,
    )

    with caplog.at_level(logging.INFO):
        await retire.execute("boiler", LIAM)

    held = done.held
    assert held.state == "both" and held.new_memory_id is not None
    saved = world.new(held.new_memory_id)
    assert (saved.user_id, saved.scope, saved.content, saved.origin) == ("emma", "household", held.content, "tool")
    assert saved.is_active and world.embed.embedded == [saved]
    # Reflection's change on it is still dropped: nobody asked for that one.
    assert reflected.state == "dropped"
    assert f"Memory proposal saved | memory=boiler proposal={held.id}" in caplog.text


# --- Emma takes her waiting sentence back ✕, and Undo ------------------------------------------


def _withdraw(world):
    from app.domain.use_cases.memories.withdraw_change import RestoreChangeUseCase, WithdrawChangeUseCase

    args = dict(proposal_repo=world.proposals, memory_repo=world.memories, uow=world.uow)
    return WithdrawChangeUseCase(**args), RestoreChangeUseCase(**args)


@pytest.mark.asyncio
async def test_GIVEN_emmas_sentence_waits_WHEN_she_withdraws_it_and_undoes_THEN_it_waits_again(caplog):
    world = World(scores={"boiler": 0.9})
    held = (await world.remember.execute(EMMA, "The boiler service moved to November.", replaces="boiler")).held
    withdraw, restore = _withdraw(world)

    with caplog.at_level(logging.INFO):
        withdrawn = await withdraw.execute(held.id, EMMA)
    assert withdrawn.state == "withdrawn"
    assert f"Memory change withdrawn | member=emma proposal={held.id}" in caplog.text

    restored = await restore.execute(held.id, EMMA)
    assert restored.state == "pending"
    assert [m.id for m in world.memories.memories] == ["veg", "jury", "boiler"]


@pytest.mark.asyncio
async def test_only_emma_withdraws_her_own_waiting_remember_and_only_while_it_waits():
    from app.domain.entities.memory_proposal import MemoryProposal

    world = World(scores={"boiler": 0.9})
    held = (await world.remember.execute(EMMA, "The boiler service moved to November.", replaces="boiler")).held
    reflected = await world.proposals.create(
        MemoryProposal(memory_id="boiler", content="Booked for spring.", answerer_id="liam", source_user_id="emma")
    )
    withdraw, restore = _withdraw(world)

    with pytest.raises(ZeroLeakViolationException):
        await withdraw.execute(held.id, LIAM)
    with pytest.raises(ProposalStateException):
        await withdraw.execute(reflected.id, EMMA)  # reflection read it; there's nothing of hers to take back
    with pytest.raises(EntityNotFoundException):
        await withdraw.execute("nope", EMMA)
    with pytest.raises(ProposalStateException):
        await restore.execute(held.id, EMMA)  # it was never withdrawn
    held.state = "kept"
    with pytest.raises(ProposalStateException):
        await withdraw.execute(held.id, EMMA)


@pytest.mark.asyncio
async def test_GIVEN_liams_memory_is_gone_WHEN_emma_undoes_her_withdraw_THEN_it_cannot_come_back():
    world = World(scores={"boiler": 0.9})
    held = (await world.remember.execute(EMMA, "The boiler service moved to November.", replaces="boiler")).held
    withdraw, restore = _withdraw(world)
    await withdraw.execute(held.id, EMMA)
    world.boiler.is_active = False

    with pytest.raises(ProposalStateException):
        await restore.execute(held.id, EMMA)
    assert held.state == "withdrawn"


# --- "Forget the November thing" while it only waits on Liam ------------------------------------


class MeaningEmbedder:
    """Near and far by meaning, as bge-m3 would place them: the November sentences together, all else apart."""

    def __init__(self):
        self.calls = []

    async def embed(self, texts):
        self.calls.append(list(texts))
        return [[1.0, 0.1] if "November" in t else [0.0, 1.0] for t in texts]


async def _waiting(world):
    return (await world.remember.execute(EMMA, "The boiler service moved to November.", replaces="boiler")).held


@pytest.mark.asyncio
async def test_GIVEN_emmas_sentence_waits_WHEN_she_forgets_it_loosely_THEN_her_waiting_one_is_found_by_meaning():
    from app.domain.use_cases.memories.remember_forget import HeldMatch

    embedder = MeaningEmbedder()
    # Liam's boiler memory is the closest stored one; Emma can't forget it, but her own waiting one she can.
    world = World(scores={"boiler": 0.9}, embedder=embedder)
    held = await _waiting(world)
    embedder.calls.clear()

    found = await world.forget.find(EMMA, "the November thing")

    assert isinstance(found, HeldMatch) and found.proposal is held and found.score > 0.6
    # The query and her waiting sentences go to the embedder together, once.
    assert embedder.calls == [["the November thing", "The boiler service moved to November."]]

    forgotten = await world.forget.execute(EMMA, "the November thing", proposal_id=held.id)
    assert held.state == "withdrawn" and forgotten.content == held.content
    assert world.boiler.is_active


@pytest.mark.asyncio
async def test_GIVEN_emmas_sentence_waits_WHEN_she_forgets_something_else_THEN_liams_still_refuses_by_name():
    world = World(scores={"boiler": 0.9}, embedder=MeaningEmbedder())
    held = await _waiting(world)

    with pytest.raises(ZeroLeakViolationException, match="Only Liam can remove that memory"):
        await world.forget.find(EMMA, "the boiler thing")
    assert held.state == "pending"


@pytest.mark.asyncio
async def test_GIVEN_the_embedder_is_down_WHEN_she_quotes_her_waiting_sentence_THEN_it_is_still_found():
    from app.domain.use_cases.memories.remember_forget import HeldMatch

    from app.domain.entities.memory_proposal import MemoryProposal

    world = World(embedder=FakeEmbedder(fail=True))
    held = await world.proposals.create(
        MemoryProposal(memory_id="boiler", content="The boiler service moved to November.", answerer_id="liam",
                       source_user_id="emma", origin="tool")
    )

    found = await world.forget.find(EMMA, "the boiler service moved to november.")

    assert isinstance(found, HeldMatch) and found.proposal is held


@pytest.mark.asyncio
async def test_a_forget_card_for_a_waiting_sentence_shows_whose_it_waits_on_and_pins_the_change():
    world = World(scores={"boiler": 0.9}, embedder=MeaningEmbedder())
    held = await _waiting(world)

    shown, pinned, refusal = await _card(world).execute(EMMA, "forget", {"memory": "the November thing"})

    assert pinned == {"proposal_id": held.id} and refusal is None
    assert shown["stored"]["content"] == held.content and shown["stored"]["mine"] is True
    assert shown["stored"]["waiting_on"] == "Liam" and shown["stored"]["said_by"] == "Emma"


@pytest.mark.asyncio
async def test_a_card_on_a_memory_rebuilt_from_its_event_sends_the_events_start_for_the_phone_to_show():
    from datetime import datetime, timezone

    from app.domain.entities.memory import TIMED

    world = World(scores={"jury": 0.9})
    world.jury.content, world.jury.time_from_event = "Studio Jury", TIMED
    world.jury.event_starts_at = datetime(2026, 10, 17, 7, 0, tzinfo=timezone.utc)

    shown, _, _ = await _card(world).execute(EMMA, "forget", {"memory": "the jury"})

    assert shown["stored"]["content"] == "Studio Jury"
    assert shown["stored"]["event_starts_at"] == "2026-10-17T07:00:00+00:00"
    assert shown["stored"]["time_from_event"] == TIMED
