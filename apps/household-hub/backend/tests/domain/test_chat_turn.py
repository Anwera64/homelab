import pytest
from typing import Any, Dict, List
from datetime import datetime, timezone

from app.domain.entities.user import User
from app.domain.entities.agent import AgentPersonality
from app.domain.entities.session import ConversationSession, ChatMessage
from app.domain.entities.llm_message import (
    LLMMessage,
    LLMResponse,
    LLMResponseChunk,
    LLMToolCall,
)
from app.domain.entities.tool_definition import ToolDefinition, ToolExecutionResult
from app.domain.exceptions import (
    EntityNotFoundException,
    ZeroLeakViolationException,
    InvalidOperationException,
)
from app.domain.use_cases.chat.process_chat_turn import ProcessChatTurnUseCase


class FakeSessionRepository:
    def __init__(self, sessions=None, messages=None):
        self.sessions = {s.id: s for s in (sessions or [])}
        self.messages = list(messages or [])

    async def get_by_id(self, session_id: str):
        return self.sessions.get(session_id)

    async def add_message(self, message: ChatMessage) -> ChatMessage:
        self.messages.append(message)
        return message

    async def list_messages_by_session_id(self, session_id: str, limit: int = 50, before_id=None):
        return [m for m in self.messages if m.session_id == session_id][:limit]

    get_messages = list_messages_by_session_id


    async def update(self, session: ConversationSession) -> ConversationSession:
        self.sessions[session.id] = session
        return session


class FakeAgentRepository:
    def __init__(self, agents=None):
        self.agents = {a.id: a for a in (agents or [])}

    async def get_by_id(self, agent_id: str):
        return self.agents.get(agent_id)


class FakeLLMClient:
    def __init__(self, responses=None, stream_chunks_list=None):
        # responses is a list of LLMResponse objects to return sequentially
        self.responses = list(responses or [])
        self.stream_chunks_list = list(stream_chunks_list or [])
        self.chat_calls = []
        self.stream_calls = []

    async def chat_completion(self, messages, model, temperature=0.7, top_p=0.9, tools=None):
        self.chat_calls.append({"messages": messages, "model": model, "tools": tools})
        if self.responses:
            return self.responses.pop(0)
        return LLMResponse(content="Default response")

    async def stream_chat_completion(self, messages, model, temperature=0.7, top_p=0.9, tools=None):
        self.stream_calls.append({"messages": messages, "model": model, "tools": tools})
        if self.stream_chunks_list:
            chunks = self.stream_chunks_list.pop(0)
            for chunk in chunks:
                yield chunk
        else:
            resp = await self.chat_completion(messages, model, temperature, top_p, tools)
            yield LLMResponseChunk(delta_content=resp.content, tool_calls=resp.tool_calls)


class FakeContextAssembler:
    async def execute(self, user, agent, recent_messages, is_secret_session=False, is_turn_secret=False):
        return [
            LLMMessage(role="system", content="System instructions"),
            *[LLMMessage(role=m.role, content=m.content) for m in recent_messages],
        ]


class FakeToolExecutor:
    def __init__(self):
        self.executed_calls = []

    async def execute(self, tool_name, arguments, user_id, agent_tool_permissions, is_secret_mode=False, role="assistant"):
        self.executed_calls.append({"tool": tool_name, "args": arguments})
        return ToolExecutionResult(tool_name=tool_name, success=True, data={"result": "ok"})


class FakeToolLister:
    async def execute(self):
        return [
            ToolDefinition(name="calendar_read", description="Read calendar", parameters_schema={"type": "object"}),
            ToolDefinition(name="calendar_write", description="Write calendar", parameters_schema={"type": "object"}),
        ]


class FakeUnitOfWork:
    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        pass

    async def commit(self):
        pass


@pytest.mark.asyncio
async def test_process_chat_turn_simple():
    user = User(id="u1", username="alex")
    agent = AgentPersonality(id="a1", name="Assistant", model_alias="qwen3:14b")
    session = ConversationSession(id="s1", user_id="u1", agent_id="a1")

    session_repo = FakeSessionRepository(sessions=[session])
    agent_repo = FakeAgentRepository(agents=[agent])
    llm_client = FakeLLMClient(responses=[LLMResponse(content="Hello Alex! How can I help?")])
    tool_executor = FakeToolExecutor()

    use_case = ProcessChatTurnUseCase(
        session_repo=session_repo,
        agent_repo=agent_repo,
        llm_client=llm_client,
        context_assembler=FakeContextAssembler(),
        tool_executor=tool_executor,
        tool_lister=FakeToolLister(),
        uow=FakeUnitOfWork(),
    )

    result = await use_case.execute(session_id="s1", current_user=user, content="Hello")

    assert result.message.role == "assistant"
    assert result.message.content == "Hello Alex! How can I help?"
    assert len(session_repo.messages) == 2  # user message + assistant message
    assert session_repo.messages[0].content == "Hello"
    assert session_repo.messages[1].content == "Hello Alex! How can I help?"


@pytest.mark.asyncio
async def test_process_chat_turn_privacy_trigger():
    user = User(id="u1", username="alex")
    agent = AgentPersonality(id="a1", name="Assistant")
    session = ConversationSession(id="s1", user_id="u1", agent_id="a1", is_secret=False)

    session_repo = FakeSessionRepository(sessions=[session])
    agent_repo = FakeAgentRepository(agents=[agent])
    llm_client = FakeLLMClient(responses=[LLMResponse(content="I will keep this confidential.")])

    use_case = ProcessChatTurnUseCase(
        session_repo=session_repo,
        agent_repo=agent_repo,
        llm_client=llm_client,
        context_assembler=FakeContextAssembler(),
        tool_executor=FakeToolExecutor(),
        tool_lister=FakeToolLister(),
        uow=FakeUnitOfWork(),
    )

    result = await use_case.execute(
        session_id="s1", current_user=user, content="Keep this between us, I am buying a gift for Maria."
    )

    assert result.privacy_trigger_detected is True
    assert result.suggest_secret_mode is True
    assert result.is_turn_secret is True


@pytest.mark.asyncio
async def test_process_chat_turn_with_tool_execution():
    user = User(id="u1", username="alex")
    agent = AgentPersonality(id="a1", name="Assistant", tool_permissions=["calendar_read"])
    session = ConversationSession(id="s1", user_id="u1", agent_id="a1")

    session_repo = FakeSessionRepository(sessions=[session])
    agent_repo = FakeAgentRepository(agents=[agent])

    # Turn 1: Model calls calendar_read
    tc = LLMToolCall(id="c1", name="calendar_read", arguments={"date": "today"})
    resp1 = LLMResponse(content="", tool_calls=[tc])
    # Turn 2: Model gives final reply
    resp2 = LLMResponse(content="You have 2 meetings today.")

    llm_client = FakeLLMClient(responses=[resp1, resp2])
    tool_executor = FakeToolExecutor()

    use_case = ProcessChatTurnUseCase(
        session_repo=session_repo,
        agent_repo=agent_repo,
        llm_client=llm_client,
        context_assembler=FakeContextAssembler(),
        tool_executor=tool_executor,
        tool_lister=FakeToolLister(),
        uow=FakeUnitOfWork(),
    )

    result = await use_case.execute(session_id="s1", current_user=user, content="Check my calendar")

    assert len(tool_executor.executed_calls) == 1
    assert tool_executor.executed_calls[0]["tool"] == "calendar_read"
    assert result.message.content == "You have 2 meetings today."
    assert len(result.tools_executed) == 1
    assert result.tools_executed[0]["tool"] == "calendar_read"


@pytest.mark.asyncio
async def test_process_chat_turn_write_tool_confirmation():
    user = User(id="u1", username="alex")
    agent = AgentPersonality(id="a1", name="Assistant", tool_permissions=["calendar_write"])
    session = ConversationSession(id="s1", user_id="u1", agent_id="a1")

    session_repo = FakeSessionRepository(sessions=[session])
    agent_repo = FakeAgentRepository(agents=[agent])

    tc = LLMToolCall(id="c1", name="calendar_write", arguments={"title": "Doctor"})
    resp1 = LLMResponse(content="", tool_calls=[tc])
    resp2 = LLMResponse(content="I have prepared the calendar event proposal.")

    llm_client = FakeLLMClient(responses=[resp1, resp2])
    tool_executor = FakeToolExecutor()

    use_case = ProcessChatTurnUseCase(
        session_repo=session_repo,
        agent_repo=agent_repo,
        llm_client=llm_client,
        context_assembler=FakeContextAssembler(),
        tool_executor=tool_executor,
        tool_lister=FakeToolLister(),
        uow=FakeUnitOfWork(),
    )

    # Without auto_approve_writes=True, write tool is proposed, not executed
    result = await use_case.execute(
        session_id="s1", current_user=user, content="Add doctor appointment", auto_approve_writes=False
    )
    assert len(tool_executor.executed_calls) == 0  # Not executed directly
    assert len(result.tools_executed) == 1
    assert result.tools_executed[0]["status"] == "proposal_pending"


@pytest.mark.asyncio
async def test_execute_stream_progressive_token_streaming_no_tools():
    user = User(id="u1", username="alex")
    agent = AgentPersonality(id="a1", name="Assistant", tool_permissions=[])
    session = ConversationSession(id="s1", user_id="u1", agent_id="a1")

    session_repo = FakeSessionRepository(sessions=[session])
    agent_repo = FakeAgentRepository(agents=[agent])
    stream_chunks = [
        LLMResponseChunk(delta_content="Hello "),
        LLMResponseChunk(delta_content="there, "),
        LLMResponseChunk(delta_content="friend!"),
    ]
    llm_client = FakeLLMClient(stream_chunks_list=[stream_chunks])
    tool_executor = FakeToolExecutor()

    use_case = ProcessChatTurnUseCase(
        session_repo=session_repo,
        agent_repo=agent_repo,
        llm_client=llm_client,
        context_assembler=FakeContextAssembler(),
        tool_executor=tool_executor,
        tool_lister=FakeToolLister(),
        uow=FakeUnitOfWork(),
    )

    events = [
        ev
        async for ev in use_case.execute_stream(
            session_id="s1", current_user=user, content="Hello"
        )
    ]

    # Verify no blocking chat_completion was called when agent has no tools
    assert len(llm_client.chat_calls) == 0
    assert len(llm_client.stream_calls) == 1

    # Verify progressive deltas were emitted
    delta_contents = [ev["content"] for ev in events if ev["type"] == "delta"]
    assert delta_contents == ["Hello ", "there, ", "friend!"]

    # Verify done event and persistence
    done_ev = [ev for ev in events if ev["type"] == "done"][0]
    assert done_ev["assistant_content"] == "Hello there, friend!"
    assert session_repo.messages[-1].content == "Hello there, friend!"


@pytest.mark.asyncio
async def test_execute_stream_tool_execution_followed_by_streamed_synthesis():
    user = User(id="u1", username="alex")
    agent = AgentPersonality(id="a1", name="Assistant", tool_permissions=["calendar_read"])
    session = ConversationSession(id="s1", user_id="u1", agent_id="a1")

    session_repo = FakeSessionRepository(sessions=[session])
    agent_repo = FakeAgentRepository(agents=[agent])

    # Turn 1: chat_completion triggers tool call
    tc = LLMToolCall(id="c1", name="calendar_read", arguments={"date": "today"})
    resp1 = LLMResponse(content="", tool_calls=[tc])

    # Synthesis: streamed chunks after tool result
    stream_chunks = [
        LLMResponseChunk(delta_content="You "),
        LLMResponseChunk(delta_content="have "),
        LLMResponseChunk(delta_content="2 meetings."),
    ]
    llm_client = FakeLLMClient(responses=[resp1], stream_chunks_list=[stream_chunks])
    tool_executor = FakeToolExecutor()

    use_case = ProcessChatTurnUseCase(
        session_repo=session_repo,
        agent_repo=agent_repo,
        llm_client=llm_client,
        context_assembler=FakeContextAssembler(),
        tool_executor=tool_executor,
        tool_lister=FakeToolLister(),
        uow=FakeUnitOfWork(),
    )

    events = [
        ev
        async for ev in use_case.execute_stream(
            session_id="s1", current_user=user, content="Check my calendar"
        )
    ]

    # Tool call detection used chat_completion
    assert len(llm_client.chat_calls) == 1
    # Synthesis used stream_chat_completion with tools=None
    assert len(llm_client.stream_calls) == 1
    assert llm_client.stream_calls[0]["tools"] is None

    # Events sequence: tool_executing, tool_result, deltas, done
    types = [ev["type"] for ev in events]
    assert "tool_executing" in types
    assert "tool_result" in types
    deltas = [ev["content"] for ev in events if ev["type"] == "delta"]
    assert deltas == ["You ", "have ", "2 meetings."]
    done_ev = [ev for ev in events if ev["type"] == "done"][0]
    assert done_ev["assistant_content"] == "You have 2 meetings."

