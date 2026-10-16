"""Changes to check (memory spec §6.1, §8): who sees which change, where it came from, and a chat's look-over."""

from datetime import timedelta

import pytest

from app.domain.entities.agent import AgentPersonality
from app.domain.entities.memory_proposal import MemoryProposal
from app.domain.entities.session import ConversationSession
from app.domain.entities.user import User
from app.domain.exceptions import EntityNotFoundException
from app.domain.use_cases.memories.changes_to_check import ListProposalsUseCase, LookedOverUseCase
from app.domain.use_cases.memories.list_memory_page import ListMemoryPageUseCase
from tests.domain.reflection_fakes import NOW, FakeMemories, FakeProposals, FakeUsers, memory

EMMA = User(id="emma", full_name="Emma")
LIAM = User(id="liam", full_name="Liam")
SAM = User(id="sam", full_name="Sam")


class FakeChats:
    def __init__(self, chats):
        self.chats = {c.id: c for c in chats}

    async def get_by_id(self, session_id):
        return self.chats.get(session_id)


class FakeAgents:
    async def get_by_id(self, agent_id):
        return {"planner": AgentPersonality(id="planner", name="Planner", avatar="🗓️")}.get(agent_id)


def _proposal(pid, memory_id, answerer, source_user, chat, content, minutes=0, state="pending", new_memory_id=None):
    return MemoryProposal(
        id=pid,
        memory_id=memory_id,
        content=content,
        relation="changed",
        answerer_id=answerer,
        source_session_id=chat,
        source_user_id=source_user,
        state=state,
        new_memory_id=new_memory_id,
        created_at=NOW - timedelta(minutes=minutes),
    )


class World:
    """Emma's "Dinner ideas" chat read a change to her Sam memory and one to Liam's boiler memory;
    Liam's "House jobs" chat read a change to Emma's bins memory."""

    def __init__(self):
        sam = memory("sam", "Sam is vegetarian and doesn't eat fish.", user_id="emma", scope="household")
        sam.agent_id = "planner"
        self.memories = FakeMemories([
            sam,
            memory("boiler", "The boiler service is booked yearly in October.", user_id="liam", scope="household"),
            memory("bins", "Bins go out on Tuesday.", user_id="emma", scope="household"),
            memory("dairy", "Is off dairy this month.", user_id="emma"),
        ])
        self.proposals = FakeProposals([
            _proposal("p-sam", "sam", "emma", "emma", "dinner", "Sam started eating fish again.", minutes=5),
            _proposal("p-boiler", "boiler", "liam", "emma", "dinner", "The boiler moved to November.", minutes=3),
            _proposal("p-bins", "bins", "emma", "liam", "house", "Bins go out on Wednesday.", minutes=1),
        ])
        self.chats = FakeChats([
            ConversationSession(
                id="dinner", user_id="emma", agent_id="planner", title="Dinner ideas",
                reflected_through_id="msg-9", reflected_at=NOW,
                last_reflection={"added": ["dairy"], "proposed": ["p-sam", "p-boiler"], "retired": [], "at": NOW.isoformat()},
            ),
            ConversationSession(id="house", user_id="liam", title="House jobs"),
        ])
        self.users = FakeUsers([EMMA, LIAM, SAM])
        self.list = ListProposalsUseCase(self.proposals, self.memories, self.chats, self.users, FakeAgents())
        self.looked_over = LookedOverUseCase(self.proposals, self.memories, self.chats, self.users, FakeAgents())


@pytest.mark.asyncio
async def test_GIVEN_changes_WHEN_emma_lists_THEN_she_answers_hers_and_sees_liams_waiting():
    world = World()

    listed = await world.list.execute(EMMA)

    assert [c.proposal.id for c in listed.to_check] == ["p-bins", "p-sam"]  # newest first
    assert all(c.can_answer for c in listed.to_check)
    assert [c.proposal.id for c in listed.waiting] == ["p-boiler"]
    waiting = listed.waiting[0]
    assert not waiting.can_answer and waiting.answerer_name == "Liam"
    assert waiting.stored.speaker_name == "Liam" and not waiting.stored.is_mine


@pytest.mark.asyncio
async def test_GIVEN_a_change_WHEN_listed_THEN_the_chat_title_shows_to_its_owner_only():
    world = World()

    emma = {c.proposal.id: c for c in (await world.list.execute(EMMA)).to_check}
    liam = {c.proposal.id: c for c in (await world.list.execute(LIAM)).to_check}

    assert (emma["p-sam"].source_title, emma["p-sam"].source_owner_name) == ("Dinner ideas", "Emma")
    # From Liam's chat: Emma sees only his name, never the title.
    assert (emma["p-bins"].source_title, emma["p-bins"].source_owner_name) == (None, "Liam")
    assert (liam["p-boiler"].source_title, liam["p-boiler"].source_owner_name) == (None, "Emma")


@pytest.mark.asyncio
async def test_GIVEN_changes_WHEN_others_list_THEN_each_sees_only_theirs():
    world = World()

    liam = await world.list.execute(LIAM)
    sam = await world.list.execute(SAM)

    assert [c.proposal.id for c in liam.to_check] == ["p-boiler"]
    assert [c.proposal.id for c in liam.waiting] == ["p-bins"]
    assert sam.to_check == [] and sam.waiting == []


@pytest.mark.asyncio
async def test_GIVEN_a_stored_memory_WHEN_listed_THEN_its_card_says_who_said_it_and_which_agent():
    world = World()

    card = next(c for c in (await world.list.execute(EMMA)).to_check if c.proposal.id == "p-sam")

    assert card.stored.memory.content == "Sam is vegetarian and doesn't eat fish."
    assert (card.stored.speaker_name, card.stored.is_mine, card.stored.agent_name) == ("Emma", True, "Planner")


@pytest.mark.asyncio
async def test_GIVEN_a_looked_over_chat_WHEN_summarised_THEN_the_line_counts_whats_remembered_and_to_check():
    world = World()
    chat = world.chats.chats["dinner"]

    summary = await world.looked_over.summary(chat, EMMA)

    assert summary.through_message_id == "msg-9" and summary.at == NOW
    assert (summary.remembered, summary.to_check) == (1, 1)  # Liam's boiler change isn't Emma's to check


@pytest.mark.asyncio
async def test_GIVEN_a_change_answered_with_use_new_WHEN_summarised_THEN_its_memory_counts_as_remembered():
    world = World()
    new = memory("new-sam", "Sam started eating fish again.", user_id="emma", scope="household")
    world.memories.memories.append(new)
    world.proposals.created[0].state = "replaced"
    world.proposals.created[0].new_memory_id = "new-sam"

    summary = await world.looked_over.summary(world.chats.chats["dinner"], EMMA)

    assert (summary.remembered, summary.to_check) == (2, 0)


@pytest.mark.asyncio
async def test_GIVEN_a_remembered_memory_removed_WHEN_summarised_THEN_it_no_longer_counts():
    world = World()
    world.memories.memories[3].is_active = False

    summary = await world.looked_over.summary(world.chats.chats["dinner"], EMMA)

    assert summary.remembered == 0


@pytest.mark.asyncio
async def test_GIVEN_a_chat_never_looked_over_or_secret_WHEN_summarised_THEN_there_is_no_line():
    world = World()
    secret = world.chats.chats["dinner"]
    secret.is_secret = True

    assert await world.looked_over.summary(world.chats.chats["house"], LIAM) is None
    assert await world.looked_over.summary(secret, EMMA) is None


@pytest.mark.asyncio
async def test_GIVEN_a_looked_over_chat_WHEN_its_sheet_opens_THEN_it_holds_to_check_waiting_and_remembered():
    world = World()

    sheet = await world.looked_over.execute("dinner", EMMA)

    assert sheet.at == NOW
    assert [c.proposal.id for c in sheet.to_check] == ["p-sam"]
    assert [c.proposal.id for c in sheet.waiting] == ["p-boiler"]
    assert [i.memory.id for i in sheet.remembered] == ["dairy"]
    assert sheet.remembered[0].can_remove


@pytest.mark.asyncio
async def test_GIVEN_someone_elses_chat_WHEN_its_sheet_is_asked_for_THEN_it_is_not_found():
    world = World()

    with pytest.raises(EntityNotFoundException):
        await world.looked_over.execute("dinner", LIAM)
    with pytest.raises(EntityNotFoundException):
        await world.looked_over.execute("house", LIAM)  # never looked over


class _PageMemories:
    async def list_current_personal(self, user_id, now):
        return []

    async def list_current_household(self, now):
        return []


@pytest.mark.asyncio
async def test_GIVEN_changes_WHEN_the_memory_page_is_listed_THEN_it_counts_the_members_to_check():
    world = World()
    page = ListMemoryPageUseCase(_PageMemories(), world.users, FakeAgents(), proposal_repo=world.proposals)

    assert (await page.execute(EMMA)).to_check == 2
    assert (await page.execute(LIAM)).to_check == 1
