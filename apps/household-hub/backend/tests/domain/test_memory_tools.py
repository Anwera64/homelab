"""`remember` and `forget` as agent tools: offered, asked about, carded and run (memory spec §2.1)."""

import json

import pytest

from app.domain.entities.agent import AgentPersonality
from app.domain.entities.llm_message import LLMResponseChunk, LLMToolCall
from app.domain.entities.memory import AgentMemory
from app.domain.entities.tool_definition import ToolDefinition
from app.domain.entities.user import User
from app.domain.exceptions import EntityNotFoundException, ZeroLeakViolationException
from app.domain.use_cases.chat.process_chat_turn import ProcessChatTurnUseCase
from app.domain.use_cases.chat.tool_approval_settings import ListToolApprovalsUseCase, SetToolApprovalUseCase
from app.domain.use_cases.chat.tool_summary import describe_write
from app.domain.use_cases.integrations.execute_tool import ExecuteToolUseCase, effective_tool_permissions
from app.domain.use_cases.integrations.list_available_tools import ListAvailableToolsUseCase
from app.domain.use_cases.memories.remember_forget import Remembered
from tests.domain.test_auto_approve import FakeToolApprovalRepository
from tests.domain.test_chat_turn import (
    FakeAgentRepository,
    FakeContextAssembler,
    FakeLLMClient,
    FakeToolExecutor,
    FakeUnitOfWork,
    _house_resolver,
)
from tests.domain.test_integration_use_cases import (
    MockCalendarConnector,
    MockCalendarCredentialRepository,
    MockDocumentReader,
    MockDocumentRepository,
    MockSearchConnector,
    MockSecretCipher,
    MockUnitOfWork,
)
from tests.domain.test_tool_approval import USER, SavingSessionRepository, _answers, _ask, _decide
from app.domain.entities.session import ConversationSession

STORED = {
    "content": "Sam is vegetarian and doesn't eat fish.",
    "said_by": "Liam",
    "mine": False,
    "agent": "Planner",
    "created_at": "2026-03-02T10:00:00+00:00",
}
VEGAN = LLMToolCall(
    id="m1",
    name="remember",
    arguments={"content": "Sam is vegan now.", "scope": "household", "replaces": "Sam is vegetarian"},
)
FORGET_JURY = LLMToolCall(id="m2", name="forget", arguments={"memory": "the jury thing"})


# --- What the model is offered ---------------------------------------------------------------


def test_the_model_is_offered_remember_and_forget_with_what_each_needs():
    tools = {t.name: t for t in ListAvailableToolsUseCase().execute()}

    remember = tools["remember"].parameters_schema
    assert remember["required"] == ["content"]
    assert remember["properties"]["scope"]["enum"] == ["personal", "household"]
    assert set(remember["properties"]) >= {"content", "scope", "category", "replaces", "expires_at"}
    assert tools["forget"].parameters_schema["required"] == ["memory"]


@pytest.mark.parametrize("granted", [[], ["calendar_read"]])
def test_every_agent_may_remember_and_forget_whatever_it_was_given(granted):
    assert {"remember", "forget"} <= set(effective_tool_permissions(granted))


# --- Asking first ------------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_remembering_can_be_made_automatic_and_forgetting_always_asks():
    repo = FakeToolApprovalRepository()
    listed = await ListToolApprovalsUseCase(repo).execute(USER)

    assert [(a.tool, a.action, a.always_asks) for a in listed if a.tool in ("remember", "forget")] == [
        ("remember", "create", False),
        ("forget", "delete", True),
    ]
    after = await SetToolApprovalUseCase(repo, FakeUnitOfWork()).execute(USER, "remember", "create", True)
    assert ("remember", "create") in [(a.tool, a.action) for a in after if a.auto]


def test_a_remember_step_names_the_sentence_and_a_forget_step_the_memory_it_forgot():
    assert describe_write("remember", {"content": "Sam is vegan now."}) == {"action": "create", "title": "Sam is vegan now."}
    assert describe_write("forget", {"memory": "the jury", "stored": {"content": "Studio Jury on Friday."}}) == {
        "action": "delete",
        "title": "Studio Jury on Friday.",
    }
    assert describe_write("forget", {"memory": "the jury"}) == {"action": "delete", "title": "the jury"}


# --- Running -----------------------------------------------------------------------------------


class FakeRemember:
    def __init__(self):
        self.calls = []

    async def execute(self, member, content, **kwargs):
        self.calls.append({"member": member.id, "content": content, **kwargs})
        old = AgentMemory(id="veg", user_id="liam", content="Sam is vegetarian.")
        return Remembered(memory=AgentMemory(id="new", content=content), kept_beside=old, speaker_name="Liam")


class FakeForget:
    def __init__(self, refuse=None):
        self.refuse = refuse
        self.calls = []

    async def execute(self, member, text, memory_id=None):
        self.calls.append((member.id, text, memory_id))
        if self.refuse:
            raise self.refuse
        return AgentMemory(id="jury", user_id=member.id, content="Studio Jury on Friday.")


def _executor(remember=None, forget=None):
    return ExecuteToolUseCase(
        calendar_repo=MockCalendarCredentialRepository(),
        calendar_connector=MockCalendarConnector(),
        search_connector=MockSearchConnector(),
        document_repo=MockDocumentRepository(),
        document_reader=MockDocumentReader(),
        cipher=MockSecretCipher(),
        uow=MockUnitOfWork(),
        remember=remember,
        forget=forget,
    )


@pytest.mark.asyncio
async def test_remember_runs_for_the_member_in_their_chat_and_tells_the_model_who_will_be_asked():
    remember = FakeRemember()

    result = await _executor(remember=remember).execute(
        tool_name="remember",
        arguments={**VEGAN.arguments, "memory_id": "veg", "category": "preference"},
        user_id="emma",
        agent_tool_permissions=[],
        session_id="chat",
        agent_id="planner",
    )

    assert result.success
    assert remember.calls == [
        {
            "member": "emma",
            "content": "Sam is vegan now.",
            "scope": "household",
            "category": "preference",
            "replaces": "Sam is vegetarian",
            "memory_id": "veg",
            "expires_at": None,
            "session_id": "chat",
            "agent_id": "planner",
        }
    ]
    assert result.data["action"] == "remembered"
    assert "Liam will be asked which one is right" in result.data["kept_beside"]


@pytest.mark.asyncio
async def test_forget_runs_on_the_memory_its_card_showed():
    forget = FakeForget()

    result = await _executor(forget=forget).execute(
        tool_name="forget",
        arguments={"memory": "the jury thing", "memory_id": "jury"},
        user_id="emma",
        agent_tool_permissions=[],
    )

    assert result.success and result.data == {"action": "forgot", "memory": "Studio Jury on Friday."}
    assert forget.calls == [("emma", "the jury thing", "jury")]


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "refusal, reason",
    [
        (EntityNotFoundException("No stored memory matches that. Nothing was forgotten."), "not_found"),
        (ZeroLeakViolationException("Only Liam can remove that memory. Nothing was forgotten."), "forbidden"),
    ],
)
async def test_a_forget_that_cannot_happen_fails_and_says_why(refusal, reason):
    result = await _executor(forget=FakeForget(refuse=refusal)).execute(
        tool_name="forget", arguments={"memory": "x"}, user_id="emma", agent_tool_permissions=[]
    )

    assert not result.success
    assert result.error == refusal.message
    assert result.reason == reason


@pytest.mark.asyncio
@pytest.mark.parametrize("tool", ["remember", "forget"])
async def test_nothing_is_remembered_or_forgotten_in_a_secret_turn(tool):
    remember, forget = FakeRemember(), FakeForget()

    result = await _executor(remember, forget).execute(
        tool_name=tool, arguments={"content": "x", "memory": "x"}, user_id="emma", agent_tool_permissions=[], is_secret_mode=True
    )

    assert not result.success
    assert remember.calls == [] and forget.calls == []


# --- The card ----------------------------------------------------------------------------------


class MemoryToolLister:
    async def execute(self):
        return [ToolDefinition(name=name, description=name, parameters_schema={"type": "object"}) for name in ("remember", "forget")]


class FakeMemoryCard:
    """What the hub looks up for a memory card: what it shows, the id it pins, or why there is no card."""

    def __init__(self, shown=None, pinned=None, refusal=None):
        self.shown = shown or {}
        self.pinned = pinned or {}
        self.refusal = refusal
        self.asked = []

    async def execute(self, member, tool, arguments):
        self.asked.append((member.id, tool, dict(arguments)))
        return self.shown, self.pinned, self.refusal


def _turn(rounds, memory_card=None, approvals=None):
    session_repo = SavingSessionRepository(sessions=[ConversationSession(id="s1", user_id="u1", agent_id="a1")])
    llm_client = FakeLLMClient(stream_chunks_list=rounds)
    tool_executor = FakeToolExecutor()
    use_case = ProcessChatTurnUseCase(
        session_repo=session_repo,
        # An agent given no tools at all still remembers.
        agent_repo=FakeAgentRepository(agents=[AgentPersonality(id="a1", name="Planner", tool_permissions=[])]),
        llm_client=llm_client,
        context_assembler=FakeContextAssembler(),
        tool_executor=tool_executor,
        tool_lister=MemoryToolLister(),
        model_resolver=_house_resolver(),
        uow=FakeUnitOfWork(),
        approval_repo=approvals,
        memory_card=memory_card,
    )
    return use_case, session_repo, llm_client, tool_executor


@pytest.mark.asyncio
async def test_an_agent_with_no_tools_is_offered_remember_and_forget():
    use_case, _, llm_client, _ = _turn([[LLMResponseChunk(delta_content="Hi.")]])

    await _ask(use_case, "Hello")

    offered = [tool["function"]["name"] for tool in llm_client.stream_calls[0]["tools"]]
    assert offered == ["remember", "forget"]


@pytest.mark.asyncio
async def test_a_remember_card_shows_the_memory_it_changes_after_a_looked_through_step():
    card = FakeMemoryCard(shown={"stored": STORED}, pinned={"memory_id": "veg"})
    use_case, session_repo, _, tool_executor = _turn([[LLMResponseChunk(tool_calls=[VEGAN])]], memory_card=card)

    events = await _ask(use_case, "Remember that Sam is vegan now")

    assert card.asked == [("u1", "remember", VEGAN.arguments)]
    [proposal] = [ev for ev in events if ev["type"] == "tool_approval_proposal"]
    assert proposal["arguments"] == {**VEGAN.arguments, "memory_id": "veg", "stored": STORED}
    assert proposal["action"] == "create"
    types = [ev["type"] for ev in events]
    [step] = [ev for ev in events if ev["type"] == "tool_result"]
    assert step["data"]["tool"] == "memory_lookup" and step["data"]["success"]
    assert types.index("tool_result") < types.index("tool_approval_proposal")
    assert tool_executor.executed_calls == []
    [answer] = _answers(session_repo)
    assert [part["type"] for part in answer.metadata_json["parts"]] == ["tool", "proposal"]
    assert answer.metadata_json["parts"][0]["tool"] == "memory_lookup"


@pytest.mark.asyncio
async def test_GIVEN_a_remember_card_approved_THEN_it_runs_on_the_memory_the_card_showed_not_what_it_displayed():
    card = FakeMemoryCard(shown={"stored": STORED}, pinned={"memory_id": "veg"})
    use_case, _, _, tool_executor = _turn(
        [[LLMResponseChunk(tool_calls=[VEGAN])], [LLMResponseChunk(delta_content="Saved.")]], memory_card=card
    )
    await _ask(use_case, "Remember that Sam is vegan now")

    await _decide(use_case, "m1", approved=True)

    [ran] = tool_executor.executed_calls
    assert ran["tool"] == "remember"
    assert ran["args"] == {**VEGAN.arguments, "memory_id": "veg"}


@pytest.mark.asyncio
async def test_GIVEN_a_forget_with_nothing_the_member_may_forget_THEN_there_is_no_card_and_the_model_is_told():
    card = FakeMemoryCard(refusal="Only Liam can remove that memory. Nothing was forgotten.")
    use_case, _, llm_client, tool_executor = _turn(
        [[LLMResponseChunk(tool_calls=[FORGET_JURY])], [LLMResponseChunk(delta_content="Only Liam can.")]],
        memory_card=card,
    )

    events = await _ask(use_case, "Forget the jury thing")

    assert not any(ev["type"] == "tool_approval_proposal" for ev in events)
    assert tool_executor.executed_calls == []
    told = llm_client.stream_calls[1]["messages"][-1]
    assert told.role == "tool" and "Only Liam can remove that memory" in json.loads(told.content)["error"]
    assert events[-1]["type"] == "done"


@pytest.mark.asyncio
async def test_GIVEN_remembering_is_automatic_THEN_it_runs_with_no_card_and_no_lookup_step():
    card = FakeMemoryCard(shown={"stored": STORED}, pinned={"memory_id": "veg"})
    use_case, _, _, tool_executor = _turn(
        [[LLMResponseChunk(tool_calls=[VEGAN])], [LLMResponseChunk(delta_content="Saved.")]],
        memory_card=card,
        approvals=FakeToolApprovalRepository([("u1", "remember", "create")]),
    )

    events = await _ask(use_case, "Remember that Sam is vegan now")

    assert card.asked == []
    assert [call["tool"] for call in tool_executor.executed_calls] == ["remember"]
    assert not any(ev["type"] == "tool_approval_proposal" for ev in events)


@pytest.mark.asyncio
async def test_GIVEN_forgetting_was_somehow_saved_as_automatic_THEN_it_still_asks():
    use_case, _, _, tool_executor = _turn(
        [[LLMResponseChunk(tool_calls=[FORGET_JURY])]],
        memory_card=FakeMemoryCard(shown={"stored": STORED}, pinned={"memory_id": "jury"}),
        approvals=FakeToolApprovalRepository([("u1", "forget", "delete")]),
    )

    events = await _ask(use_case, "Forget the jury thing")

    assert tool_executor.executed_calls == []
    assert events[-1]["type"] == "awaiting_approval"
