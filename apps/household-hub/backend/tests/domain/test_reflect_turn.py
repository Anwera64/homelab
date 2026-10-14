import json
import pytest
from datetime import datetime, timezone, timedelta

from app.domain.entities.session import ConversationSession
from app.domain.entities.memory import AgentMemory
from app.domain.entities.llm_message import LLMResponse
from app.domain.entities.llm_model import LLMModel
from app.domain.use_cases.memories.reflect_turn import ReflectTurnUseCase
from app.domain.use_cases.models.resolve_agent_model import ResolveAgentModelUseCase


class FakeLLMClient:
    def __init__(self, response_json_str: str):
        self.response_json_str = response_json_str

        self.models_asked = []
        self.messages_sent = []

    async def chat_completion(self, messages, model, temperature=0.7, top_p=0.9, tools=None):
        self.models_asked.append(model)
        self.messages_sent.append(messages)
        return LLMResponse(content=self.response_json_str)


class FakeLLMModelRepository:
    async def get_default(self):
        return LLMModel(id="m1", provider_model="house-model", display_name="House", is_default=True)

    async def get_by_id(self, model_id: str):
        return None


def _house_resolver():
    return ResolveAgentModelUseCase(FakeLLMModelRepository())


class FakeMemoryRepository:
    def __init__(self):
        self.memories = []

    async def create(self, memory: AgentMemory) -> AgentMemory:
        self.memories.append(memory)
        return memory

    async def list_user_memories(self, user_id, scope=None, agent_id=None, category=None, active_only=True):
        return [m for m in self.memories if m.user_id == user_id]

    async def update(self, memory: AgentMemory) -> AgentMemory:
        for i, m in enumerate(self.memories):
            if m.id == memory.id:
                self.memories[i] = memory
                return memory
        self.memories.append(memory)
        return memory



class FakeSessionRepository:
    def __init__(self, session: ConversationSession):
        self.session = session

    async def get_by_id(self, session_id: str):
        return self.session if self.session.id == session_id else None

    async def update(self, session: ConversationSession) -> ConversationSession:
        self.session = session
        return session


class FakeUnitOfWork:
    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        pass

    async def commit(self):
        pass


@pytest.mark.asyncio
async def test_reflect_turn_extracts_memories_and_shared_facts():
    extraction_output = json.dumps({
        "title": "Dietary Preferences and Jury",
        "memories": [
            {
                "category": "preference",
                "content": "Loves oat milk in cappuccino",
                "scope": "personal",
                "confidence": 0.95,
            },
            {
                "category": "fact",
                "content": "Conversational greeting",
                "scope": "personal",
                "confidence": 0.50,  # Below 0.70 threshold -> should be filtered
            },
            {
                "category": "project",
                "content": "UPC Thesis Jury presentation on Friday 16 October",
                "scope": "household",
                "confidence": 0.9,
                "expires_at": "2026-10-16T18:00:00Z",
            },
        ],
        # A model that still answers in the old shape changes nothing (memory spec §3.2).
        "milestones": [
            {
                "category": "academic_deadline",
                "summary": "UPC Thesis Jury presentation on Friday",
            }
        ],
    })

    session = ConversationSession(id="s1", user_id="u1", agent_id="a1", title="New Conversation")
    llm = FakeLLMClient(response_json_str=extraction_output)
    mem_repo = FakeMemoryRepository()
    sess_repo = FakeSessionRepository(session=session)
    uow = FakeUnitOfWork()

    use_case = ReflectTurnUseCase(
        llm_client=llm,
        memory_repo=mem_repo,
        session_repo=sess_repo,
        uow=uow,
        model_resolver=_house_resolver(),
        confidence_threshold=0.70,
    )

    result = await use_case.execute(
        session_id="s1",
        user_id="u1",
        username="alex",
        agent_id="a1",
        agent_name="Researcher",
        user_message="I love oat milk in cappuccino, and my UPC Thesis Jury is on Friday.",
        assistant_message="Noted!",
        is_secret_session=False,
        is_turn_secret=False,
        is_first_turn=True,
    )

    # 0. Reflection asks the household default model, whatever it is called
    assert llm.models_asked == ["house-model"]

    # 1. Title was updated on first turn
    assert session.title == "Dietary Preferences and Jury"
    assert result.session_title == "Dietary Preferences and Jury"

    # 2. Memories filtered by confidence, all from reflection
    assert [m.content for m in result.memories_created] == [
        "Loves oat milk in cappuccino",
        "UPC Thesis Jury presentation on Friday 16 October",
    ]
    assert {m.origin for m in result.memories_created} == {"reflection"}

    # 3. A shared fact is a household memory with who said it and when it stops mattering
    shared = result.memories_created[1]
    assert shared.scope == "household"
    assert shared.user_id == "u1" and shared.agent_id == "a1"
    assert shared.expires_at == datetime(2026, 10, 16, 18, 0, tzinfo=timezone.utc)
    assert result.memories_created[0].expires_at is None

    # 4. Nothing else was written: there is one store
    assert mem_repo.memories == result.memories_created
    assert not hasattr(result, "milestones_created")


@pytest.mark.asyncio
async def test_reflect_turn_secret_mode_barrier():
    extraction_output = json.dumps({
        "memories": [
            {
                "category": "milestone",
                "content": "Secret surprise party planned for Maria",
                "scope": "household",  # In secret mode, must be forced to personal!
                "confidence": 0.95,
            }
        ],
        "milestones": [
            {
                "category": "milestone",
                "summary": "Secret party on Saturday",
            }
        ],
    })

    session = ConversationSession(id="s1", user_id="u1", agent_id="a1", is_secret=True)
    llm = FakeLLMClient(response_json_str=extraction_output)
    mem_repo = FakeMemoryRepository()
    sess_repo = FakeSessionRepository(session=session)
    uow = FakeUnitOfWork()

    use_case = ReflectTurnUseCase(
        llm_client=llm,
        memory_repo=mem_repo,
        session_repo=sess_repo,
        uow=uow,
        model_resolver=_house_resolver(),
    )

    result = await use_case.execute(
        session_id="s1",
        user_id="u1",
        username="alex",
        agent_id="a1",
        agent_name="Assistant",
        user_message="Keep this between us, I'm planning a surprise party for Maria on Saturday.",
        assistant_message="Understood, it is safe with me.",
        is_secret_session=True,
        is_turn_secret=True,
        is_first_turn=False,
    )

    # Memory scope must be forced to personal, and nothing reaches the household
    assert len(mem_repo.memories) == 1
    assert mem_repo.memories[0].scope == "personal"


@pytest.mark.asyncio
async def test_reflect_turn_memory_deduplication():
    # Pre-populate memory repository with existing active memory
    existing_mem = AgentMemory(
        id="mem_exist_1",
        user_id="u1",
        scope="personal",
        category="preference",
        content="Loves oat milk in cappuccino",
        confidence=0.75,
    )
    mem_repo = FakeMemoryRepository()
    await mem_repo.create(existing_mem)

    extraction_output = json.dumps({
        "memories": [
            {
                "category": "preference",
                "content": "loves oat milk in cappuccino",  # Same content, different case
                "scope": "personal",
                "confidence": 0.95,
            }
        ],
    })

    session = ConversationSession(id="s1", user_id="u1", agent_id="a1")
    llm = FakeLLMClient(response_json_str=extraction_output)
    sess_repo = FakeSessionRepository(session=session)
    uow = FakeUnitOfWork()

    use_case = ReflectTurnUseCase(
        llm_client=llm,
        memory_repo=mem_repo,
        session_repo=sess_repo,
        uow=uow,
        model_resolver=_house_resolver(),
    )

    result = await use_case.execute(
        session_id="s1",
        user_id="u1",
        username="alex",
        agent_id="a1",
        agent_name="Assistant",
        user_message="I really love oat milk in cappuccino, remember that!",
        assistant_message="Got it!",
    )

    # Must NOT create a duplicate memory row
    assert len(mem_repo.memories) == 1
    assert len(result.memories_created) == 0
    assert len(result.memories_updated) == 1
    assert result.memories_updated[0].id == "mem_exist_1"
    assert result.memories_updated[0].confidence == 0.95


@pytest.mark.asyncio
async def test_a_bad_expiry_is_saved_without_one():
    """A date the model wrote badly must not lose the memory; it just never expires."""
    extraction_output = json.dumps({
        "memories": [
            {"content": "Dinner at Mario's on Saturday", "scope": "household", "confidence": 0.9, "expires_at": "Saturday"},
        ],
    })
    mem_repo = FakeMemoryRepository()
    use_case = ReflectTurnUseCase(
        llm_client=FakeLLMClient(response_json_str=extraction_output),
        memory_repo=mem_repo,
        session_repo=FakeSessionRepository(session=ConversationSession(id="s1", user_id="u1", agent_id="a1")),
        uow=FakeUnitOfWork(),
        model_resolver=_house_resolver(),
    )

    await use_case.execute(
        session_id="s1",
        user_id="u1",
        username="alex",
        agent_id="a1",
        agent_name="Assistant",
        user_message="Dinner is on Saturday!",
        assistant_message="Nice!",
    )

    assert [(m.content, m.expires_at) for m in mem_repo.memories] == [("Dinner at Mario's on Saturday", None)]


FIXED_NOW = datetime(2026, 9, 24, 20, 5, tzinfo=timezone.utc)


async def _reflect_and_capture_system_prompt(timezone_name=None) -> str:
    llm = FakeLLMClient(response_json_str=json.dumps({"memories": []}))
    use_case = ReflectTurnUseCase(
        llm_client=llm,
        memory_repo=FakeMemoryRepository(),
        session_repo=FakeSessionRepository(session=ConversationSession(id="s1", user_id="u1", agent_id="a1")),
        uow=FakeUnitOfWork(),
        model_resolver=_house_resolver(),
        clock=lambda: FIXED_NOW,
    )
    await use_case.execute(
        session_id="s1",
        user_id="u1",
        username="alex",
        agent_id="a1",
        agent_name="Researcher",
        user_message="My thesis jury is on Friday.",
        assistant_message="Good luck!",
        timezone_name=timezone_name,
    )
    return "\n\n".join(m.content for m in llm.messages_sent[0] if m.role == "system")


@pytest.mark.asyncio
async def test_reflection_is_told_the_date_so_friday_and_expiry_land_on_real_days():
    """
    #39: without today's date the model guessed the year for `expires_at`, and a household fact
    could come out already expired - so no other agent ever heard of it.
    """
    system = await _reflect_and_capture_system_prompt(timezone_name="America/Mexico_City")

    assert "Today is Thursday, 24 September 2026, 14:05 (America/Mexico_City)." in system
    assert "absolute" in system and "expires_at" in system.split("Today is", 1)[1]


@pytest.mark.asyncio
async def test_reflection_without_a_timezone_is_dated_in_utc():
    system = await _reflect_and_capture_system_prompt()

    assert "Today is Thursday, 24 September 2026, 20:05 (UTC)." in system
