"""The memory page (memory spec §8, §10 "Remove and Undo"): what each member sees, and ✕ with Undo."""

import logging
from datetime import datetime, timedelta, timezone

import pytest

from app.domain.entities.agent import AgentPersonality
from app.domain.entities.memory import AgentMemory
from app.domain.entities.user import User
from app.domain.exceptions import EntityNotFoundException, InvalidOperationException, ZeroLeakViolationException
from app.domain.use_cases.memories.list_memory_page import ListMemoryPageUseCase
from app.domain.use_cases.memories.restore_memory import RestoreMemoryUseCase
from app.domain.use_cases.memories.retire_memory import RetireMemoryUseCase

NOW = datetime(2026, 10, 15, 18, 0, tzinfo=timezone.utc)
EMMA = User(id="emma", full_name="Emma Larsson")
LIAM = User(id="liam", full_name="Liam Larsson", is_admin=True)


class FakeMemoryRepo:
    def __init__(self, memories):
        self.memories = {m.id: m for m in memories}
        self.updated: list[str] = []

    async def get_by_id(self, memory_id):
        return self.memories.get(memory_id)

    async def update(self, memory):
        self.memories[memory.id] = memory
        self.updated.append(memory.id)
        return memory

    async def list_current_personal(self, user_id, now):
        return [m for m in self.memories.values() if m.scope == "personal" and m.user_id == user_id]

    async def list_current_household(self, now):
        return [m for m in self.memories.values() if m.scope == "household"]


class FakeUserRepo:
    async def get_by_id(self, user_id):
        return {"emma": EMMA, "liam": LIAM}.get(user_id)


class FakeAgentRepo:
    async def get_by_id(self, agent_id):
        agents = {"coord": AgentPersonality(id="coord", name="Coordinator", avatar="🏡")}
        return agents.get(agent_id)


class FakeUow:
    commits = 0

    async def __aenter__(self):
        return self

    async def __aexit__(self, *exc):
        return False

    async def commit(self):
        self.commits += 1


def _retire(repo):
    return RetireMemoryUseCase(repo, FakeUow(), clock=lambda: NOW)


# --- ✕ retires --------------------------------------------------------------------------------------


@pytest.mark.asyncio
@pytest.mark.parametrize("scope", ["personal", "household"])
async def test_the_member_who_said_it_retires_their_memory(scope):
    """GIVEN Emma's memory WHEN Emma taps ✕ THEN it is inactive with retired_at, and still stored."""
    repo = FakeMemoryRepo([AgentMemory(id="m1", user_id="emma", scope=scope, content="Likes tea")])

    await _retire(repo).execute(memory_id="m1", current_user=EMMA)

    assert repo.memories["m1"].is_active is False
    assert repo.memories["m1"].retired_at == NOW


@pytest.mark.asyncio
@pytest.mark.parametrize("scope", ["personal", "household"])
async def test_no_one_else_can_retire_a_memory_not_even_an_admin(scope):
    """GIVEN Emma's memory WHEN Liam, an admin, removes it THEN he is refused and nothing changes."""
    repo = FakeMemoryRepo([AgentMemory(id="m1", user_id="emma", scope=scope, content="Likes tea")])

    with pytest.raises(ZeroLeakViolationException):
        await _retire(repo).execute(memory_id="m1", current_user=LIAM)

    assert repo.memories["m1"].is_active is True
    assert repo.updated == []


@pytest.mark.asyncio
async def test_retiring_an_unknown_memory_is_not_found():
    with pytest.raises(EntityNotFoundException):
        await _retire(FakeMemoryRepo([])).execute(memory_id="nope", current_user=EMMA)


# --- Undo -------------------------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_undo_brings_a_removed_memory_back():
    """GIVEN a memory Emma removed WHEN she taps Undo THEN it is active again and retired_at is cleared."""
    repo = FakeMemoryRepo(
        [AgentMemory(id="m1", user_id="emma", content="Likes tea", is_active=False, retired_at=NOW)]
    )

    restored = await RestoreMemoryUseCase(repo, FakeUow()).execute(memory_id="m1", current_user=EMMA)

    assert restored.is_active is True
    assert restored.retired_at is None


@pytest.mark.asyncio
async def test_no_one_else_can_undo():
    repo = FakeMemoryRepo(
        [AgentMemory(id="m1", user_id="emma", scope="household", content="x", is_active=False, retired_at=NOW)]
    )

    with pytest.raises(ZeroLeakViolationException):
        await RestoreMemoryUseCase(repo, FakeUow()).execute(memory_id="m1", current_user=LIAM)


@pytest.mark.asyncio
async def test_a_memory_replaced_by_another_cannot_be_undone():
    """GIVEN a memory retired in favour of a newer one WHEN Undo is asked THEN it is refused."""
    repo = FakeMemoryRepo(
        [AgentMemory(id="m1", user_id="emma", content="x", is_active=False, retired_at=NOW, replaced_by_id="m2")]
    )

    with pytest.raises(InvalidOperationException):
        await RestoreMemoryUseCase(repo, FakeUow()).execute(memory_id="m1", current_user=EMMA)


@pytest.mark.asyncio
async def test_undo_on_a_memory_that_was_never_removed_changes_nothing():
    repo = FakeMemoryRepo([AgentMemory(id="m1", user_id="emma", content="x")])

    restored = await RestoreMemoryUseCase(repo, FakeUow()).execute(memory_id="m1", current_user=EMMA)

    assert restored.is_active is True
    assert repo.updated == []


# --- The page ---------------------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_the_page_splits_yours_from_the_household_with_names():
    """GIVEN memories by Emma and Liam WHEN Emma opens the page THEN Yours is hers, Household is everyone's with who said it."""
    repo = FakeMemoryRepo(
        [
            AgentMemory(id="p1", user_id="emma", agent_id="coord", content="Prefers dinner after 20:00"),
            AgentMemory(id="p2", user_id="liam", content="Liam's private note"),
            AgentMemory(id="h1", user_id="emma", agent_id="coord", scope="household", content="Dinner Saturday"),
            AgentMemory(id="h2", user_id="liam", agent_id="gone", scope="household", content="Boiler in October"),
        ]
    )

    page = await ListMemoryPageUseCase(repo, FakeUserRepo(), FakeAgentRepo(), clock=lambda: NOW).execute(EMMA)

    assert [i.memory.id for i in page.yours] == ["p1"]
    yours = page.yours[0]
    assert (yours.agent_name, yours.agent_avatar, yours.can_remove) == ("Coordinator", "🏡", True)

    household = {i.memory.id: i for i in page.household}
    assert set(household) == {"h1", "h2"}
    assert (household["h1"].speaker_name, household["h1"].is_mine, household["h1"].can_remove) == (
        "Emma Larsson",
        True,
        True,
    )
    assert (household["h2"].speaker_name, household["h2"].is_mine, household["h2"].can_remove) == (
        "Liam Larsson",
        False,
        False,
    )


@pytest.mark.asyncio
async def test_a_memory_whose_agent_is_gone_reads_an_agent():
    repo = FakeMemoryRepo([AgentMemory(id="p1", user_id="emma", agent_id=None, content="x")])

    page = await ListMemoryPageUseCase(repo, FakeUserRepo(), FakeAgentRepo(), clock=lambda: NOW).execute(EMMA)

    assert (page.yours[0].agent_name, page.yours[0].agent_avatar) == ("an agent", "🤖")


# --- Logging (§9): ids and outcomes, never the memory's text ---------------------------------------


@pytest.mark.asyncio
async def test_remove_and_undo_log_ids_but_not_the_text(caplog):
    repo = FakeMemoryRepo([AgentMemory(id="m1", user_id="emma", content="Secret-ish sentence")])

    with caplog.at_level(logging.INFO, logger="app"):
        await _retire(repo).execute(memory_id="m1", current_user=EMMA)
        await RestoreMemoryUseCase(repo, FakeUow()).execute(memory_id="m1", current_user=EMMA)
        with pytest.raises(ZeroLeakViolationException):
            await _retire(repo).execute(memory_id="m1", current_user=LIAM)

    lines = [r.getMessage() for r in caplog.records]
    assert "Memory removed | member=emma memory=m1" in lines
    assert "Memory restored | member=emma memory=m1" in lines
    assert "Memory remove refused | member=liam memory=m1 reason=not_theirs" in lines
    assert not any("Secret-ish" in line for line in lines)


@pytest.mark.asyncio
async def test_listing_the_page_logs_counts(caplog):
    repo = FakeMemoryRepo([AgentMemory(id="p1", user_id="emma", content="Words")])

    with caplog.at_level(logging.INFO, logger="app"):
        await ListMemoryPageUseCase(repo, FakeUserRepo(), FakeAgentRepo(), clock=lambda: NOW).execute(EMMA)

    assert "Memory page listed | member=emma yours=1 household=0 to_check=0 waiting=0" in [
        r.getMessage() for r in caplog.records
    ]


@pytest.mark.asyncio
async def test_GIVEN_a_pending_proposal_WHEN_its_memory_is_removed_THEN_the_proposal_is_dropped(caplog):
    """A proposal is dropped, not answered, when its stored memory is removed (memory spec §3.5)."""
    from tests.domain.reflection_fakes import FakeProposals
    from app.domain.entities.memory_proposal import MemoryProposal

    proposals = FakeProposals()
    await proposals.create(MemoryProposal(memory_id="veg", content="Robin is vegan", answerer_id="emma"))
    repo = FakeMemoryRepo([AgentMemory(id="veg", user_id="emma", content="Robin is vegetarian")])

    with caplog.at_level(logging.INFO):
        await RetireMemoryUseCase(repo, FakeUow(), clock=lambda: NOW, proposal_repo=proposals).execute("veg", EMMA)

    assert proposals.dropped_for == ["veg"]
    assert proposals.created[0].state == "dropped"
    assert "reason=memory_removed" in caplog.text
