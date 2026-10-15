"""The fixed block (memory spec §5.1): what an agent always knows, filled to a token budget."""

import logging
from datetime import datetime, timedelta, timezone

import pytest

from app.domain.entities.agent import AgentPersonality
from app.domain.entities.memory import AgentMemory
from app.domain.entities.user import User
from app.domain.use_cases.chat.assemble_agent_context import AssembleAgentContextUseCase
from app.domain.use_cases.chat.token_estimate import estimate_tokens
from app.domain.use_cases.memories.memory_block import BuildMemoryBlockUseCase
from tests.domain.test_assemble_context import FakeMemoryRepository, FakeUserRepository

NOW = datetime(2026, 10, 15, 12, 0, tzinfo=timezone.utc)
EMMA = User(id="emma", full_name="Emma")
AGENT = AgentPersonality(id="a1", name="Planner", system_prompt="Plan things.")


def _memory(memory_id, days_old, category="fact", scope="personal", user_id="emma", **kwargs):
    return AgentMemory(
        id=memory_id, user_id=user_id, scope=scope, category=category,
        content=f"Memory {memory_id} " + "x" * 30, created_at=NOW - timedelta(days=days_old), **kwargs,
    )


def _builder(personal=(), household=(), budget=1000):
    return BuildMemoryBlockUseCase(
        FakeMemoryRepository(personal=list(personal), household=list(household)),
        user_repo=FakeUserRepository([EMMA, User(id="liam", full_name="Liam")]),
        budget_tokens=budget,
        clock=lambda: NOW,
    )


@pytest.mark.asyncio
async def test_GIVEN_a_small_budget_THEN_preferences_and_health_go_in_first_whatever_their_age():
    old_preference = _memory("pref", 400, category="preference")
    old_health = _memory("health", 300, category="health", scope="household")
    newest_fact = _memory("fact", 0)
    builder = _builder(personal=[newest_fact, old_preference], household=[old_health], budget=45)

    block = await builder.execute("emma", is_secret=False)

    assert [m.id for m in block.included] == ["health", "pref"]
    assert [m.id for m in block.left_out] == ["fact"]


@pytest.mark.asyncio
async def test_GIVEN_more_than_fits_THEN_the_rest_go_newest_first_and_the_block_stays_within_budget():
    facts = [_memory(f"f{age}", age) for age in range(10)]
    block = await _builder(personal=facts, budget=60).execute("emma", is_secret=False)

    assert [m.id for m in block.included] == ["f0", "f1", "f2"]
    assert block.tokens_used <= 60
    assert block.tokens_used == pytest.approx(sum(estimate_tokens(block.lines[m.id]) for m in block.included))
    assert [m.id for m in block.left_out] == [f"f{age}" for age in range(3, 10)]


@pytest.mark.asyncio
async def test_GIVEN_a_memory_that_does_not_fit_THEN_the_block_stops_there():
    """Predictable: a smaller one after it isn't squeezed in."""
    big = _memory("big", 1)
    big.content = "y" * 300
    small = _memory("small", 2)
    block = await _builder(personal=[_memory("new", 0), big, small], budget=40).execute("emma", is_secret=False)

    assert [m.id for m in block.included] == ["new"]
    assert [m.id for m in block.left_out] == ["big", "small"]


@pytest.mark.asyncio
async def test_expired_and_inactive_memories_never_go_in_or_out():
    expired = _memory("expired", 1, expires_at=NOW - timedelta(minutes=1))
    retired = _memory("retired", 1, is_active=False)
    block = await _builder(personal=[expired, retired, _memory("ok", 2)]).execute("emma", is_secret=False)

    assert [m.id for m in block.included] == ["ok"]
    assert block.left_out == []


@pytest.mark.asyncio
async def test_another_members_personal_memories_never_and_household_ones_only_outside_secret():
    mine = _memory("mine", 1)
    liams = _memory("liams", 1, user_id="liam")
    shared = _memory("shared", 1, scope="household", user_id="liam")
    builder = _builder(personal=[mine, liams], household=[shared])

    assert {m.id for m in (await builder.execute("emma", is_secret=False)).included} == {"mine", "shared"}
    assert {m.id for m in (await builder.execute("emma", is_secret=True)).included} == {"mine"}


@pytest.mark.asyncio
async def test_lines_say_who_said_a_household_memory_and_never_print_confidence():
    shared = _memory("shared", 1, scope="household", user_id="liam", confidence=0.83)
    mine = _memory("mine", 1, confidence=0.91)
    block = await _builder(personal=[mine], household=[shared]).execute("emma", is_secret=False)

    assert block.lines["shared"].startswith("Liam said to an agent: Memory shared")
    assert block.lines["mine"].startswith("[Added Oct 2026] Memory mine")
    assert "0.83" not in block.lines["shared"] and "confidence" not in block.lines["mine"]


@pytest.mark.asyncio
async def test_GIVEN_a_budget_WHEN_assembled_THEN_the_prompt_holds_only_what_fit_and_the_block_is_logged(caplog):
    facts = [_memory(f"f{age}", age) for age in range(10)]
    assembler = AssembleAgentContextUseCase(
        FakeMemoryRepository(personal=facts),
        user_repo=FakeUserRepository([EMMA]),
        clock=lambda: NOW,
        memory_block_tokens=60,
    )

    with caplog.at_level(logging.INFO):
        system = (await assembler.execute(user=EMMA, agent=AGENT, recent_messages=[]))[0].content

    assert "Memory f2" in system and "Memory f3" not in system
    assert "Memory block | member=emma" in caplog.text
    assert "in=3 left_out=7" in caplog.text
    assert "Memory f0" not in caplog.text


@pytest.mark.asyncio
async def test_GIVEN_more_than_twenty_five_small_household_memories_THEN_all_fit_the_default_budget():
    """The old 20 / 25 row limits are gone: the budget decides."""
    household = [_memory(f"h{age}", age, scope="household") for age in range(30)]
    assembler = AssembleAgentContextUseCase(
        FakeMemoryRepository(household=household), user_repo=FakeUserRepository([EMMA]), clock=lambda: NOW
    )

    system = (await assembler.execute(user=EMMA, agent=AGENT, recent_messages=[]))[0].content

    assert "Memory h29" in system
