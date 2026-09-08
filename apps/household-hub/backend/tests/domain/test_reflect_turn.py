import json
import pytest
from datetime import datetime, timezone, timedelta

from app.domain.entities.session import ConversationSession
from app.domain.entities.memory import AgentMemory
from app.domain.entities.gossip_milestone import GossipMilestone
from app.domain.entities.llm_message import LLMResponse
from app.domain.use_cases.memories.reflect_turn import ReflectTurnUseCase


class FakeLLMClient:
    def __init__(self, response_json_str: str):
        self.response_json_str = response_json_str

    async def chat_completion(self, messages, model, temperature=0.7, top_p=0.9, tools=None):
        return LLMResponse(content=self.response_json_str)


class FakeMemoryRepository:
    def __init__(self):
        self.memories = []

    async def create(self, memory: AgentMemory) -> AgentMemory:
        self.memories.append(memory)
        return memory

    async def list_user_memories(self, user_id, scope=None, agent_id=None, category=None, active_only=True):
        return [m for m in self.memories if m.user_id == user_id]


class FakeGossipRepository:
    def __init__(self):
        self.milestones = []

    async def publish(self, milestone: GossipMilestone) -> GossipMilestone:
        self.milestones.append(milestone)
        return milestone

    async def get_active_household_milestones(self, limit=50, now=None):
        return [m for m in self.milestones if m.is_active]


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
async def test_reflect_turn_extracts_memories_and_milestones():
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
        ],
        "milestones": [
            {
                "category": "academic_deadline",
                "summary": "UPC Thesis Jury presentation on Friday",
                "expires_at": "2026-10-14T18:00:00Z",
            }
        ],
    })

    session = ConversationSession(id="s1", user_id="u1", agent_id="a1", title="New Conversation")
    llm = FakeLLMClient(response_json_str=extraction_output)
    mem_repo = FakeMemoryRepository()
    gossip_repo = FakeGossipRepository()
    sess_repo = FakeSessionRepository(session=session)
    uow = FakeUnitOfWork()

    use_case = ReflectTurnUseCase(
        llm_client=llm,
        memory_repo=mem_repo,
        gossip_repo=gossip_repo,
        session_repo=sess_repo,
        uow=uow,
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

    # 1. Title was updated on first turn
    assert session.title == "Dietary Preferences and Jury"
    assert result.session_title == "Dietary Preferences and Jury"

    # 2. Memories filtered by confidence
    assert len(result.memories_created) == 1
    assert result.memories_created[0].content == "Loves oat milk in cappuccino"

    # 3. Milestone was published with provenance
    assert len(result.milestones_created) == 1
    assert result.milestones_created[0].summary == "UPC Thesis Jury presentation on Friday"
    assert result.milestones_created[0].source_username == "alex"
    assert result.milestones_created[0].reporting_agent_name == "Researcher"


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
    gossip_repo = FakeGossipRepository()
    sess_repo = FakeSessionRepository(session=session)
    uow = FakeUnitOfWork()

    use_case = ReflectTurnUseCase(
        llm_client=llm,
        memory_repo=mem_repo,
        gossip_repo=gossip_repo,
        session_repo=sess_repo,
        uow=uow,
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

    # 1. Memory scope must be forced to personal
    assert len(result.memories_created) == 1
    assert result.memories_created[0].scope == "personal"

    # 2. Milestones must be strictly dropped (zero gossip)
    assert len(result.milestones_created) == 0
    assert len(gossip_repo.milestones) == 0
