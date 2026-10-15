"""
One chat's reflection (memory spec §2.2, §6): one model call over the messages after the marker, the
member's words as the only source, each fact compared with the closest stored memories and saved,
dropped or proposed.
"""

import logging

import pytest

from app.domain.entities.memory_proposal import PENDING
from app.domain.entities.session import ConversationSession
from app.domain.entities.user import User
from app.domain.use_cases.memories.memory_block import BuildMemoryBlockUseCase
from app.domain.use_cases.memories.reflect_chat import ReflectChatUseCase
from tests.domain.reflection_fakes import (
    NOW,
    FakeEmbedder,
    FakeEmbedMemories,
    FakeLLM,
    FakeMemories,
    FakeProposals,
    FakeSessions,
    FakeUnitOfWork,
    FakeUsers,
    FakeVectors,
    memory,
    message,
)

EMMA = User(id="emma", full_name="Emma")
LIAM = User(id="liam", full_name="Liam")


def _chat(**kwargs) -> ConversationSession:
    return ConversationSession(id="chat", user_id="emma", agent_id="planner", title="Plan dinners", **kwargs)


def _messages():
    return [
        message(0, "user", "My sister Robin is vegan now", minutes=0, timezone="Europe/Madrid"),
        message(1, "assistant", "Noted! I love the Sunday market too.", minutes=1),
    ]


class World:
    def __init__(self, reply=None, messages=None, memories=(), scores=None, session=None, embedder=None, content=None):
        self.llm = FakeLLM(reply, content=content)
        self.sessions = FakeSessions(session or _chat(), messages if messages is not None else _messages())
        self.memories = FakeMemories(memories)
        self.proposals = FakeProposals()
        self.embed = FakeEmbedMemories()
        self.uow = FakeUnitOfWork()
        self.embedder = embedder or FakeEmbedder()
        self.use_case = ReflectChatUseCase(
            llm_client=self.llm,
            session_repo=self.sessions,
            memory_repo=self.memories,
            proposal_repo=self.proposals,
            user_repo=FakeUsers([EMMA, LIAM]),
            memory_block=BuildMemoryBlockUseCase(self.memories, FakeUsers([EMMA, LIAM]), clock=lambda: NOW),
            embedder=self.embedder,
            vector_repo=FakeVectors(scores),
            embed_memories=self.embed,
            uow=self.uow,
            neighbour_bar=0.45,
            neighbours_max=2,
            clock=lambda: NOW,
        )

    async def run(self, keep_alive=245):
        return await self.use_case.execute(self.sessions.session, model="qwen3.8-rvn", keep_alive=keep_alive)


def _fact(content="Robin is vegan", relation="different", about=None, **extra):
    fact = {"content": content, "category": "fact", "scope": "household", "relation": relation, **extra}
    if about:
        fact["about"] = about
    return fact


# Cycle 7: saving


@pytest.mark.asyncio
async def test_GIVEN_a_new_fact_WHEN_reflected_THEN_it_is_saved_with_its_details_and_embedded():
    world = World(
        {
            "title": "Dinners for Robin",
            "facts": [
                _fact(
                    relevant_for=["planning dinner", "", 3, "buying a gift"],
                    expires_at="2026-12-01T00:00:00Z",
                    category="preference",
                )
            ],
        }
    )

    result = await world.run()

    (saved,) = world.memories.created
    assert (saved.content, saved.scope, saved.category, saved.origin) == ("Robin is vegan", "household", "preference", "reflection")
    assert (saved.user_id, saved.agent_id, saved.source_session_id) == ("emma", "planner", "chat")
    assert saved.relevant_for == ["planning dinner", "buying a gift"]
    assert saved.expires_at.isoformat() == "2026-12-01T00:00:00+00:00"
    assert world.embed.embedded == [saved]
    assert result.added == [saved.id]
    assert world.llm.calls[0]["model"] == "qwen3.8-rvn"
    assert world.llm.calls[0]["keep_alive"] == 245


@pytest.mark.asyncio
async def test_GIVEN_a_first_reflection_WHEN_titled_THEN_the_automatic_title_is_replaced_and_the_marker_written():
    world = World({"title": "Dinners for Robin", "facts": [_fact()]})

    await world.run()

    assert world.sessions.session.title == "Dinners for Robin"
    (saved,) = world.sessions.saved
    assert saved["through"] == "msg-1"
    assert saved["at"] == NOW
    assert saved["outcome"]["added"] == [world.memories.created[0].id]
    assert saved["outcome"]["proposed"] == [] and saved["outcome"]["retired"] == []
    assert saved["outcome"]["at"] == NOW.isoformat()
    assert world.uow.commits == 1


@pytest.mark.asyncio
async def test_GIVEN_a_chat_reflected_before_WHEN_reflected_again_THEN_only_new_messages_are_read_and_the_title_stays():
    session = _chat(reflected_through_id="msg-1")
    session.title = "Dinners for Robin"
    messages = _messages() + [message(2, "user", "Also, we moved the bins to Wednesdays", minutes=30)]
    world = World({"title": "Bins", "facts": []}, messages=messages, session=session)

    await world.run()

    assert "bins to Wednesdays" in world.llm.prompt
    assert "Robin is vegan now" not in world.llm.prompt
    assert world.sessions.session.title == "Dinners for Robin"
    assert world.sessions.saved[0]["through"] == "msg-2"


@pytest.mark.asyncio
async def test_GIVEN_a_transcript_WHEN_prompted_THEN_member_and_agent_lines_are_marked_with_local_times_and_the_date():
    world = World()

    await world.run()

    prompt = world.llm.prompt
    assert "Emma (Thu 15 Oct 2026, 13:00): My sister Robin is vegan now" in prompt
    assert "Agent (Thu 15 Oct 2026, 13:01, context only): Noted!" in prompt
    assert "Thursday, 15 October 2026" in prompt
    assert "Europe/Madrid" in prompt


@pytest.mark.asyncio
async def test_GIVEN_a_reply_that_is_not_json_WHEN_reflected_THEN_nothing_is_saved_and_the_marker_still_moves():
    world = World(content="I could not decide.")

    result = await world.run()

    assert world.memories.created == [] and world.proposals.created == []
    assert world.sessions.saved[0]["through"] == "msg-1"
    assert result.added == []


# Cycle 8: the member's words only, and privacy


@pytest.mark.asyncio
async def test_GIVEN_a_privacy_trigger_WHEN_reflected_THEN_that_message_and_its_answer_never_reach_the_model():
    messages = [
        message(0, "user", "Between us, I am pregnant", privacy_trigger_detected=True),
        message(1, "assistant", "Congratulations, your pregnancy is safe with me", minutes=1),
        message(2, "user", "Book the dentist for Robin", minutes=2),
        message(3, "assistant", "Done", minutes=3),
    ]
    world = World(messages=messages)

    await world.run()

    assert "pregnan" not in world.llm.prompt
    assert "Book the dentist" in world.llm.prompt
    assert world.sessions.saved[0]["through"] == "msg-3"


@pytest.mark.asyncio
async def test_GIVEN_only_private_messages_WHEN_reflected_THEN_no_model_call_and_the_marker_moves():
    messages = [message(0, "user", "Between us...", privacy_trigger_detected=True), message(1, "assistant", "Ok")]
    world = World(messages=messages)

    await world.run()

    assert world.llm.calls == []
    assert world.sessions.saved[0]["through"] == "msg-1"


@pytest.mark.asyncio
async def test_GIVEN_the_system_prompt_WHEN_read_THEN_it_says_only_the_member_is_a_source():
    world = World()

    await world.run()

    system = world.llm.calls[0]["messages"][0].content
    assert "Only what the member states" in system
    assert "never a source" in system
    assert '"relevant_for"' in system


# Cycle 9: comparing on write


@pytest.mark.asyncio
async def test_GIVEN_the_same_fact_WHEN_reflected_THEN_no_row_is_added():
    stored = memory("veg", "Robin is vegetarian", scope="household")
    world = World({"facts": [_fact("Robin is vegetarian", relation="same", about="m1")]}, memories=[stored], scores={"veg": 0.9})

    result = await world.run()

    assert world.memories.created == [] and world.proposals.created == []
    assert result.same == 1


@pytest.mark.asyncio
async def test_GIVEN_identical_text_WHEN_the_model_calls_it_different_THEN_no_row_is_added():
    stored = memory("veg", "Robin is vegetarian", scope="household")
    world = World({"facts": [_fact("robin is vegetarian ")]}, memories=[stored], scores={"veg": 0.9})

    await world.run()

    assert world.memories.created == []


@pytest.mark.asyncio
@pytest.mark.parametrize("relation", ["more_detail", "changed", "contradicts", "unclear"])
async def test_GIVEN_a_fact_that_changes_a_stored_one_WHEN_reflected_THEN_a_proposal_is_raised_and_the_memory_stays(relation):
    stored = memory("veg", "Robin is vegetarian", scope="household")
    world = World({"facts": [_fact(relation=relation, about="m1")]}, memories=[stored], scores={"veg": 0.85})

    result = await world.run()

    (proposal,) = world.proposals.created
    assert (proposal.memory_id, proposal.content, proposal.relation) == ("veg", "Robin is vegan", relation)
    assert (proposal.answerer_id, proposal.source_session_id, proposal.source_user_id) == ("emma", "chat", "emma")
    assert proposal.state == PENDING
    assert stored.is_active
    assert world.memories.created == []
    assert result.proposed == [proposal.id]
    assert world.sessions.saved[0]["outcome"]["proposed"] == [proposal.id]


@pytest.mark.asyncio
async def test_GIVEN_another_members_household_memory_WHEN_changed_THEN_that_member_answers():
    stored = memory("boiler", "Boiler service in November", user_id="liam", scope="household")
    world = World(
        {"facts": [_fact("Boiler service moved to October", relation="changed", about="m1")]},
        memories=[stored],
        scores={"boiler": 0.8},
    )

    await world.run()

    assert world.proposals.created[0].answerer_id == "liam"
    assert world.proposals.created[0].source_user_id == "emma"


@pytest.mark.asyncio
async def test_GIVEN_a_pending_proposal_WHEN_a_newer_one_is_raised_on_the_same_memory_THEN_the_older_is_dropped():
    stored = memory("veg", "Robin is vegetarian", scope="household")
    world = World({"facts": [_fact(relation="changed", about="m1")]}, memories=[stored], scores={"veg": 0.85})

    await world.run()

    assert world.proposals.dropped_for == ["veg"]


@pytest.mark.asyncio
async def test_GIVEN_an_unknown_neighbour_WHEN_a_change_names_it_THEN_the_fact_is_saved_rather_than_lost():
    world = World({"facts": [_fact(relation="changed", about="m9")]})

    await world.run()

    assert [m.content for m in world.memories.created] == ["Robin is vegan"]
    assert world.proposals.created == []


@pytest.mark.asyncio
async def test_GIVEN_stored_memories_WHEN_prompted_THEN_only_those_above_the_bar_are_offered_best_first_up_to_the_max():
    stored = [
        memory("a", "Robin is vegetarian", scope="household"),
        memory("b", "Robin likes the Sunday market"),
        memory("c", "Robin's birthday is in May"),
        memory("d", "The car needs new tyres"),
    ]
    world = World(memories=stored, scores={"a": 0.9, "b": 0.5, "c": 0.6, "d": 0.3})

    await world.run()

    prompt = world.llm.prompt
    assert "[m1] Emma said to an agent: Robin is vegetarian" in prompt
    assert "[m2] [Added Sep 2026] Robin's birthday is in May" in prompt
    assert "Sunday market" not in prompt.split("Conversation")[0]
    assert "tyres" not in prompt


@pytest.mark.asyncio
async def test_GIVEN_the_embedder_is_down_WHEN_prompted_THEN_the_newest_memories_are_offered(caplog):
    stored = [
        memory("old", "Robin is vegetarian", minutes=0),
        memory("mid", "Robin likes the Sunday market", minutes=10),
        memory("new", "Robin's birthday is in May", minutes=20),
    ]
    world = World(memories=stored, embedder=FakeEmbedder(fail=True))

    with caplog.at_level(logging.INFO):
        await world.run()

    head = world.llm.prompt.split("Conversation")[0]
    assert "birthday" in head and "Sunday market" in head and "vegetarian" not in head
    assert "neighbours=newest" in caplog.text


@pytest.mark.asyncio
async def test_GIVEN_another_members_personal_memory_WHEN_prompted_THEN_it_is_never_offered():
    world = World(memories=[memory("liams", "Liam hates mushrooms", user_id="liam")], scores={"liams": 0.95})

    await world.run()

    assert "mushrooms" not in world.llm.prompt
