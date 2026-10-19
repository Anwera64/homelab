"""Recall (memory spec §5.2): memories outside the fixed block, attached to the message they relate to."""

import asyncio
import logging
from datetime import datetime, timedelta, timezone

import pytest

from app.domain.entities.agent import AgentPersonality
from app.domain.entities.llm_message import LLMResponseChunk
from app.domain.entities.memory import AgentMemory
from app.domain.entities.session import ChatMessage, ConversationSession
from app.domain.entities.user import User
from app.domain.exceptions import EmbeddingException
from app.domain.use_cases.chat.assemble_agent_context import AssembleAgentContextUseCase
from app.domain.use_cases.chat.process_chat_turn import ProcessChatTurnUseCase
from app.domain.use_cases.memories.memory_block import BuildMemoryBlockUseCase
from app.domain.use_cases.memories.recall_memories import RecallMemoriesUseCase
from tests.domain.test_assemble_context import FakeMemoryRepository, FakeUserRepository
from tests.domain.test_chat_turn import (
    FakeAgentRepository,
    FakeLLMClient,
    FakeSessionRepository,
    FakeToolExecutor,
    FakeToolLister,
    FakeUnitOfWork,
    _house_resolver,
)

NOW = datetime(2026, 10, 15, 12, 0, tzinfo=timezone.utc)
EMMA = User(id="emma", full_name="Emma")
AGENT = AgentPersonality(id="a1", name="Planner", system_prompt="Plan things.", tool_permissions=[])


def _memory(memory_id, days_old, scope="personal", user_id="emma", content=None):
    return AgentMemory(
        id=memory_id, user_id=user_id, scope=scope,
        content=content or f"Memory {memory_id} " + "x" * 30, created_at=NOW - timedelta(days=days_old),
    )


class FakeEmbedder:
    def __init__(self, delay: float = 0.0, fail: bool = False):
        self.delay = delay
        self.fail = fail
        self.asked: list[list[str]] = []
        self.finished = 0

    async def embed(self, texts):
        self.asked.append(list(texts))
        if self.delay:
            await asyncio.sleep(self.delay)
        if self.fail:
            raise EmbeddingException("down")
        self.finished += 1
        return [[1.0, 0.0] for _ in texts]


class FakeVectorRepository:
    def __init__(self, scores):
        self.scores = scores
        self.asked_ids: list[list[str]] = []

    async def save(self, memory_id, kind, model, vector):
        pass

    async def missing(self, model):
        return []

    async def best_scores(self, query, memory_ids, model):
        self.asked_ids.append(list(memory_ids))
        return {i: s for i, s in self.scores.items() if i in memory_ids}


# Three fit in a 60-token block; the rest are left out and can only be recalled.
STORE = [_memory(f"f{age}", age) for age in range(8)]


def _recall(scores, embedder=None, personal=STORE, household=(), wait=1.0, log_text=False):
    block = BuildMemoryBlockUseCase(
        FakeMemoryRepository(personal=list(personal), household=list(household)),
        user_repo=FakeUserRepository([EMMA, User(id="liam", full_name="Liam")]),
        budget_tokens=60,
        clock=lambda: NOW,
    )
    return RecallMemoriesUseCase(
        embedder=embedder or FakeEmbedder(),
        vector_repo=FakeVectorRepository(scores),
        memory_block=block,
        model="bge-m3",
        cutoff=0.60,
        max_attached=3,
        wait_seconds=wait,
        log_text=log_text,
    )


def _question(text="What should I cook tonight?", earlier=()):
    return [*earlier, ChatMessage(id="q", session_id="s1", role="user", content=text)]


@pytest.mark.asyncio
async def test_GIVEN_a_related_memory_outside_the_block_THEN_it_is_attached_with_its_line():
    recalled = await _recall({"f5": 0.69, "f6": 0.41}).execute("emma", "s1", _question(), is_secret=False)

    assert recalled == [{"id": "f5", "text": "[Added Oct 2026] Memory f5 " + "x" * 30}]


@pytest.mark.asyncio
async def test_GIVEN_the_best_match_is_already_in_the_block_THEN_nothing_is_attached_but_its_score_is_logged(caplog):
    with caplog.at_level(logging.INFO):
        recalled = await _recall({"f0": 0.9}).execute("emma", "s1", _question(), is_secret=False)

    assert recalled == []
    assert "f0=0.900(block)" in caplog.text
    assert "reason=below_cutoff" in caplog.text


@pytest.mark.asyncio
async def test_GIVEN_an_unrelated_message_THEN_nothing_is_attached():
    assert await _recall({"f4": 0.54, "f5": 0.2}).execute("emma", "s1", _question(), is_secret=False) == []


@pytest.mark.asyncio
async def test_at_most_three_are_attached_best_first():
    scores = {"f3": 0.61, "f4": 0.7, "f5": 0.65, "f6": 0.8, "f7": 0.62}
    recalled = await _recall(scores).execute("emma", "s1", _question(), is_secret=False)

    assert [r["id"] for r in recalled] == ["f6", "f4", "f5"]


@pytest.mark.asyncio
async def test_GIVEN_a_memory_attached_earlier_in_the_window_THEN_it_is_not_attached_again():
    earlier = [
        ChatMessage(id="e1", session_id="s1", role="user", content="Dinner?",
                    metadata_json={"recalled": [{"id": "f5", "text": "..."}]}),
        ChatMessage(id="e2", session_id="s1", role="assistant", content="Pasta."),
    ]
    recalled = await _recall({"f5": 0.7, "f6": 0.65}).execute(
        "emma", "s1", _question(earlier=earlier), is_secret=False
    )

    assert [r["id"] for r in recalled] == ["f6"]


@pytest.mark.asyncio
async def test_GIVEN_a_secret_chat_THEN_only_personal_memories_are_searched():
    household = [_memory("h1", 30, scope="household", user_id="liam")]
    use_case = _recall({"h1": 0.9}, household=household)

    assert await use_case.execute("emma", "s1", _question(), is_secret=True) == []
    assert "h1" not in use_case.vector_repo.asked_ids[0]


@pytest.mark.asyncio
async def test_a_household_memory_is_recalled_with_who_said_it():
    household = [_memory("h1", 30, scope="household", user_id="liam", content="Sam is vegetarian")]
    recalled = await _recall({"h1": 0.66}, household=household).execute("emma", "s1", _question(), is_secret=False)

    assert recalled == [{"id": "h1", "text": "Liam said to an agent: Sam is vegetarian"}]


@pytest.mark.asyncio
async def test_the_question_is_the_message_plus_the_previous_exchange():
    """A follow-up like "what should I get her?" carries no meaning alone."""
    embedder = FakeEmbedder()
    earlier = [
        ChatMessage(id="e0", session_id="s1", role="user", content="Old question"),
        ChatMessage(id="e0a", session_id="s1", role="assistant", content="Old answer"),
        ChatMessage(id="e1", session_id="s1", role="user", content="It's Sam's birthday soon"),
        ChatMessage(id="e2", session_id="s1", role="assistant", content="z" * 5000),
    ]
    await _recall({}, embedder=embedder).execute("emma", "s1", _question("What should I get her?", earlier), False)

    (question,) = embedder.asked[0]
    assert question.startswith("It's Sam's birthday soon\n")
    assert question.endswith("\nWhat should I get her?")
    assert "Old" not in question
    assert len(question) < 2100


@pytest.mark.asyncio
async def test_GIVEN_nothing_stored_THEN_the_embedder_is_not_asked():
    embedder = FakeEmbedder()
    assert await _recall({}, embedder=embedder, personal=[]).execute("emma", "s1", _question(), False) == []
    assert embedder.asked == []


@pytest.mark.asyncio
async def test_GIVEN_a_slow_embedder_THEN_recall_gives_up_at_the_cap_and_lets_the_embedding_finish(caplog):
    embedder = FakeEmbedder(delay=0.2)
    with caplog.at_level(logging.INFO):
        recalled = await _recall({"f5": 0.9}, embedder=embedder, wait=0.05).execute("emma", "s1", _question(), False)

    assert recalled == []
    assert "reason=embedder_timed_out" in caplog.text
    # Not cancelled: the model ends up loaded for the next message.
    await asyncio.sleep(0.3)
    assert embedder.finished == 1


@pytest.mark.asyncio
async def test_GIVEN_the_embedder_fails_THEN_recall_attaches_nothing_and_says_why(caplog):
    with caplog.at_level(logging.INFO):
        recalled = await _recall({"f5": 0.9}, embedder=FakeEmbedder(fail=True)).execute("emma", "s1", _question(), False)

    assert recalled == []
    assert "reason=embedder_failed" in caplog.text


@pytest.mark.asyncio
async def test_GIVEN_log_text_off_THEN_no_line_carries_memory_text_and_on_THEN_it_is_cut_to_500(caplog):
    long_memory = _memory("long", 40, content="Sam is vegetarian " + "y" * 800)
    with caplog.at_level(logging.INFO):
        await _recall({"long": 0.7}, personal=STORE + [long_memory]).execute("emma", "s1", _question(), False)
    assert "Memory recall | member=emma session=s1" in caplog.text
    assert "attached=long" in caplog.text
    assert "vegetarian" not in caplog.text and "Memory f" not in caplog.text

    caplog.clear()
    with caplog.at_level(logging.INFO):
        await _recall({"long": 0.7}, personal=STORE + [long_memory], log_text=True).execute(
            "emma", "s1", _question(), False
        )
    assert "vegetarian" in caplog.text
    assert "y" * 501 not in caplog.text


# --- Wiring into the turn, and replay -------------------------------------------------------------


class SessionRepositoryThatSaves(FakeSessionRepository):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.updated: list[ChatMessage] = []

    async def update_message(self, message):
        self.updated.append(message)
        return message


class RecordingRecall:
    def __init__(self, result):
        self.result = result
        self.calls = []

    async def execute(self, user_id, session_id, messages, is_secret):
        self.calls.append((user_id, session_id, [m.content for m in messages], is_secret))
        return self.result


class RecordingLLM(FakeLLMClient):
    async def stream_chat_completion(self, messages, model, temperature=0.7, top_p=0.9, tools=None):
        self.stream_calls.append({"messages": messages})
        yield LLMResponseChunk(delta_content="Pasta with peppers.")


def _turn(session_repo, recall, llm=None):
    assembler = AssembleAgentContextUseCase(
        FakeMemoryRepository(), user_repo=FakeUserRepository([EMMA]), clock=lambda: NOW
    )
    return ProcessChatTurnUseCase(
        session_repo=session_repo,
        agent_repo=FakeAgentRepository([AGENT]),
        llm_client=llm or RecordingLLM(),
        context_assembler=assembler,
        tool_executor=FakeToolExecutor(),
        tool_lister=FakeToolLister(),
        model_resolver=_house_resolver(),
        uow=FakeUnitOfWork(),
        recall=recall,
    )


RECALLED = [{"id": "f5", "text": "[Added Oct 2026] Sam is vegetarian"}]


@pytest.mark.asyncio
async def test_GIVEN_a_new_message_THEN_recall_is_saved_on_it_and_shown_after_it_in_the_prompt():
    session_repo = SessionRepositoryThatSaves(sessions=[ConversationSession(id="s1", user_id="emma", agent_id="a1")])
    recall, llm = RecordingRecall(RECALLED), RecordingLLM()

    _ = [ev async for ev in _turn(session_repo, recall, llm).execute_stream("s1", EMMA, "What should I cook?")]

    question = session_repo.messages[0]
    assert question.metadata_json["recalled"] == RECALLED
    assert session_repo.updated == [question]
    assert recall.calls == [("emma", "s1", ["What should I cook?"], False)]
    sent = llm.stream_calls[0]["messages"][-1].content
    assert sent.endswith("What should I cook?\n\n[Memories that may help]:\n- [Added Oct 2026] Sam is vegetarian")


@pytest.mark.asyncio
async def test_GIVEN_a_secret_chat_WHEN_a_message_is_sent_THEN_recall_is_told_so():
    session_repo = SessionRepositoryThatSaves(
        sessions=[ConversationSession(id="s1", user_id="emma", agent_id="a1", is_secret=True)]
    )
    recall = RecordingRecall([])

    _ = [ev async for ev in _turn(session_repo, recall).execute_stream("s1", EMMA, "Hi")]

    assert recall.calls[0][3] is True
    assert "recalled" not in session_repo.messages[0].metadata_json
    assert session_repo.updated == []


@pytest.mark.asyncio
async def test_GIVEN_the_plain_chat_path_THEN_it_recalls_too():
    session_repo = SessionRepositoryThatSaves(sessions=[ConversationSession(id="s1", user_id="emma", agent_id="a1")])
    recall = RecordingRecall(RECALLED)

    await _turn(session_repo, recall).execute("s1", EMMA, "What should I cook?")

    assert session_repo.messages[0].metadata_json["recalled"] == RECALLED


@pytest.mark.asyncio
async def test_GIVEN_an_earlier_recall_WHEN_the_next_turn_runs_THEN_the_same_text_is_replayed_byte_for_byte():
    earlier = ChatMessage(
        id="e1", session_id="s1", role="user", content="What should I cook?",
        metadata_json={"recalled": RECALLED}, created_at=NOW,
    )
    session_repo = SessionRepositoryThatSaves(
        sessions=[ConversationSession(id="s1", user_id="emma", agent_id="a1")],
        messages=[earlier, ChatMessage(id="e2", session_id="s1", role="assistant", content="Pasta.")],
    )
    llm = RecordingLLM()

    _ = [ev async for ev in _turn(session_repo, RecordingRecall([]), llm).execute_stream("s1", EMMA, "And dessert?")]

    replayed = llm.stream_calls[0]["messages"][-3].content
    assert replayed.endswith("What should I cook?\n\n[Memories that may help]:\n- [Added Oct 2026] Sam is vegetarian")


@pytest.mark.asyncio
async def test_GIVEN_a_regenerate_THEN_recall_is_not_run_and_the_saved_one_is_replayed():
    question = ChatMessage(
        id="q", session_id="s1", role="user", content="What should I cook?",
        metadata_json={"recalled": RECALLED}, created_at=NOW,
    )
    session_repo = SessionRepositoryThatSaves(
        sessions=[ConversationSession(id="s1", user_id="emma", agent_id="a1")], messages=[question]
    )
    recall, llm = RecordingRecall([{"id": "other", "text": "other"}]), RecordingLLM()

    _ = [ev async for ev in _turn(session_repo, recall, llm).regenerate_stream("s1", EMMA)]

    assert recall.calls == []
    assert "Sam is vegetarian" in llm.stream_calls[0]["messages"][-1].content


@pytest.mark.asyncio
async def test_GIVEN_a_recalled_memory_rebuilt_from_its_event_THEN_its_time_is_in_the_zone_the_message_was_sent_in():
    from app.domain.entities.memory import TIMED

    moved = AgentMemory(
        id="dinner", user_id="emma", content="Dinner together", created_at=NOW - timedelta(days=30),
        event_starts_at=datetime(2026, 10, 19, 17, 30, tzinfo=timezone.utc), time_from_event=TIMED,
    )
    question = [ChatMessage(id="q", session_id="s1", role="user", content="When is dinner?",
                            metadata_json={"timezone": "Europe/Madrid"})]

    recalled = await _recall({"dinner": 0.8}, personal=[*STORE, moved]).execute("emma", "s1", question, is_secret=False)

    assert recalled == [{"id": "dinner", "text": "[Added Sep 2026] Dinner together on Monday 19 October, 19:30."}]
