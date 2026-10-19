"""Finding the stored memory a `remember` or `forget` call quotes (memory spec §2.1)."""

import logging

import pytest

from app.domain.entities.user import User
from app.domain.use_cases.memories.match_memory import MatchMemoryUseCase
from app.domain.use_cases.memories.memory_block import BuildMemoryBlockUseCase
from tests.domain.reflection_fakes import NOW, FakeEmbedder, FakeMemories, FakeUsers, FakeVectors, memory

EMMA = User(id="emma", full_name="Emma")
LIAM = User(id="liam", full_name="Liam")

VEGETARIAN = memory("veg", "Sam is vegetarian and doesn't eat fish.", user_id="emma", scope="household")
JURY = memory("jury", "Studio Jury on Friday 17 October, 09:00.", user_id="emma")
BOILER = memory("boiler", "The boiler service is booked yearly in October.", user_id="liam", scope="household")
LIAMS_OWN = memory("liam-own", "Liam is training for a marathon.", user_id="liam")


def _match(scores=None, embedder=None, memories=(VEGETARIAN, JURY, BOILER, LIAMS_OWN)):
    block = BuildMemoryBlockUseCase(FakeMemories(list(memories)), user_repo=FakeUsers([EMMA, LIAM]), clock=lambda: NOW)
    return MatchMemoryUseCase(
        memory_block=block,
        embedder=embedder or FakeEmbedder(),
        vector_repo=FakeVectors(scores or {}),
        model="bge-m3",
        bar=0.60,
    )


@pytest.mark.asyncio
async def test_GIVEN_the_stored_sentence_quoted_exactly_THEN_it_matches_without_asking_the_embedder():
    embedder = FakeEmbedder()

    found = await _match(embedder=embedder).execute("emma", "  sam is vegetarian and doesn't eat fish. ")

    assert found.memory.id == "veg"
    assert embedder.texts == []


@pytest.mark.asyncio
async def test_GIVEN_the_line_as_the_prompt_shows_it_THEN_it_matches():
    found = await _match().execute("emma", "[Added Sep 2026] Studio Jury on Friday 17 October, 09:00.")

    assert found.memory.id == "jury"


@pytest.mark.asyncio
async def test_GIVEN_a_rewording_THEN_the_closest_memory_above_the_bar_matches_with_its_score():
    found = await _match({"veg": 0.82, "jury": 0.3, "boiler": 0.65}).execute("emma", "Sam doesn't eat meat")

    assert found.memory.id == "veg"
    assert found.score == pytest.approx(0.82)


@pytest.mark.asyncio
async def test_GIVEN_nothing_above_the_bar_THEN_nothing_matches():
    assert await _match({"veg": 0.59, "jury": 0.2}).execute("emma", "The cat needs its jab") is None


@pytest.mark.asyncio
async def test_GIVEN_a_household_memory_liam_said_THEN_it_can_match():
    found = await _match({"boiler": 0.9}).execute("emma", "The boiler is serviced in November")

    assert found.memory.user_id == "liam"


@pytest.mark.asyncio
async def test_another_members_personal_memory_never_matches():
    assert await _match({"liam-own": 0.95}).execute("emma", "Liam is running a marathon") is None


@pytest.mark.asyncio
async def test_GIVEN_the_embedder_fails_THEN_nothing_matches_and_it_is_logged(caplog):
    with caplog.at_level(logging.WARNING):
        found = await _match({"veg": 0.9}, embedder=FakeEmbedder(fail=True)).execute("emma", "Sam eats no meat")

    assert found is None
    assert "embed failed" in caplog.text


@pytest.mark.asyncio
async def test_GIVEN_the_id_a_card_showed_THEN_that_memory_matches_whatever_the_text():
    found = await _match().execute("emma", "anything", memory_id="jury")

    assert found.memory.id == "jury"


@pytest.mark.asyncio
async def test_GIVEN_an_id_the_member_cannot_see_THEN_nothing_matches():
    assert await _match().execute("emma", "anything", memory_id="liam-own") is None


@pytest.mark.asyncio
async def test_GIVEN_a_retired_memory_THEN_it_never_matches():
    retired = memory("old", "Sam eats fish.", user_id="emma")
    retired.is_active = False

    assert await _match({"old": 0.99}, memories=(retired,)).execute("emma", "Sam eats fish.") is None
