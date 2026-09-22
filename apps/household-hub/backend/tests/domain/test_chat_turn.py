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
    LLMInferenceException,
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
    user = User(id="u1", full_name="Alex")
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
    user = User(id="u1", full_name="Alex")
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
    user = User(id="u1", full_name="Alex")
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
    user = User(id="u1", full_name="Alex")
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
    user = User(id="u1", full_name="Alex")
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
    user = User(id="u1", full_name="Alex")
    agent = AgentPersonality(id="a1", name="Assistant", tool_permissions=["calendar_read"])
    session = ConversationSession(id="s1", user_id="u1", agent_id="a1")

    session_repo = FakeSessionRepository(sessions=[session])
    agent_repo = FakeAgentRepository(agents=[agent])

    # The decision streams too now: Ollama sends the tool call whole, in one chunk.
    tc = LLMToolCall(id="c1", name="calendar_read", arguments={"date": "today"})
    decision_chunks = [LLMResponseChunk(tool_calls=[tc], finish_reason="tool_calls")]

    # Synthesis: streamed chunks after tool result
    stream_chunks = [
        LLMResponseChunk(delta_content="You "),
        LLMResponseChunk(delta_content="have "),
        LLMResponseChunk(delta_content="2 meetings."),
    ]
    llm_client = FakeLLMClient(stream_chunks_list=[decision_chunks, stream_chunks])
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

    # Nothing blocks any more: a blocking call has to finish the whole response before a single
    # byte comes back, which is what ran into the timeout while a thinking model decided.
    assert len(llm_client.chat_calls) == 0
    assert len(llm_client.stream_calls) == 2
    assert llm_client.stream_calls[0]["tools"] is not None
    # Synthesis used stream_chat_completion with tools=None
    assert llm_client.stream_calls[1]["tools"] is None

    # Events sequence: tool_executing, tool_result, deltas, done
    types = [ev["type"] for ev in events]
    assert "tool_executing" in types
    assert "tool_result" in types
    deltas = [ev["content"] for ev in events if ev["type"] == "delta"]
    assert deltas == ["You ", "have ", "2 meetings."]
    done_ev = [ev for ev in events if ev["type"] == "done"][0]
    assert done_ev["assistant_content"] == "You have 2 meetings."



@pytest.mark.asyncio
async def test_regenerate_stream_answers_again_without_asking_again():
    """
    GIVEN a question whose answer never arrived WHEN it is regenerated THEN no second question.

    The screen promises "trying again just asks for a fresh answer". Writing the user's message a
    second time would make that a lie and leave the transcript stuttering.
    """
    user = User(id="u1", full_name="Alex")
    agent = AgentPersonality(id="a1", name="Assistant", tool_permissions=[])
    session = ConversationSession(id="s1", user_id="u1", agent_id="a1")

    session_repo = FakeSessionRepository(sessions=[session])
    session_repo.messages.append(
        ChatMessage(id="m1", session_id="s1", role="user", content="Plan meals for the week")
    )

    llm_client = FakeLLMClient(
        stream_chunks_list=[[LLMResponseChunk(delta_content="Here is the week.")]]
    )

    use_case = ProcessChatTurnUseCase(
        session_repo=session_repo,
        agent_repo=FakeAgentRepository(agents=[agent]),
        llm_client=llm_client,
        context_assembler=FakeContextAssembler(),
        tool_executor=FakeToolExecutor(),
        tool_lister=FakeToolLister(),
        uow=FakeUnitOfWork(),
    )

    events = [
        ev async for ev in use_case.regenerate_stream(session_id="s1", current_user=user)
    ]

    done = [ev for ev in events if ev["type"] == "done"][0]
    assert done["assistant_content"] == "Here is the week."

    user_messages = [m for m in session_repo.messages if m.role == "user"]
    assert len(user_messages) == 1
    assert session_repo.messages[-1].role == "assistant"


@pytest.mark.asyncio
async def test_regenerate_stream_refuses_when_the_last_turn_was_answered():
    """GIVEN an answered conversation THEN there is nothing to regenerate."""
    user = User(id="u1", full_name="Alex")
    agent = AgentPersonality(id="a1", name="Assistant", tool_permissions=[])
    session = ConversationSession(id="s1", user_id="u1", agent_id="a1")

    session_repo = FakeSessionRepository(sessions=[session])
    session_repo.messages.append(
        ChatMessage(id="m1", session_id="s1", role="user", content="Plan meals")
    )
    session_repo.messages.append(
        ChatMessage(id="m2", session_id="s1", role="assistant", content="Here it is.")
    )

    use_case = ProcessChatTurnUseCase(
        session_repo=session_repo,
        agent_repo=FakeAgentRepository(agents=[agent]),
        llm_client=FakeLLMClient(stream_chunks_list=[[LLMResponseChunk(delta_content="again")]]),
        context_assembler=FakeContextAssembler(),
        tool_executor=FakeToolExecutor(),
        tool_lister=FakeToolLister(),
        uow=FakeUnitOfWork(),
    )

    with pytest.raises(InvalidOperationException):
        [ev async for ev in use_case.regenerate_stream(session_id="s1", current_user=user)]


@pytest.mark.asyncio
async def test_regenerate_stream_keeps_the_privacy_the_question_was_asked_with():
    """
    GIVEN a question that tripped a privacy phrase THEN the regenerated turn is secret too.

    The trigger was detected and stored when the question was asked; re-running the regex would
    work, but reading back what was decided the first time cannot disagree with it.
    """
    user = User(id="u1", full_name="Alex")
    agent = AgentPersonality(id="a1", name="Assistant", tool_permissions=[])
    session = ConversationSession(id="s1", user_id="u1", agent_id="a1", is_secret=False)

    session_repo = FakeSessionRepository(sessions=[session])
    session_repo.messages.append(
        ChatMessage(
            id="m1",
            session_id="s1",
            role="user",
            content="Don't tell Liam about the party",
            metadata_json={"privacy_trigger_detected": True},
        )
    )

    use_case = ProcessChatTurnUseCase(
        session_repo=session_repo,
        agent_repo=FakeAgentRepository(agents=[agent]),
        llm_client=FakeLLMClient(stream_chunks_list=[[LLMResponseChunk(delta_content="Secret.")]]),
        context_assembler=FakeContextAssembler(),
        tool_executor=FakeToolExecutor(),
        tool_lister=FakeToolLister(),
        uow=FakeUnitOfWork(),
    )

    events = [
        ev async for ev in use_case.regenerate_stream(session_id="s1", current_user=user)
    ]

    done = [ev for ev in events if ev["type"] == "done"][0]
    assert done["is_turn_secret"] is True
    assert done["suggest_secret_mode"] is True


class ExplodingLLMClient(FakeLLMClient):
    """A model that dies once the turn is already under way."""

    async def stream_chat_completion(self, messages, model, temperature=0.7, top_p=0.9, tools=None):
        self.stream_calls.append({"messages": messages, "model": model, "tools": tools})
        raise LLMInferenceException("LLM inference stream returned HTTP 500")
        yield  # pragma: no cover - makes this an async generator


@pytest.mark.asyncio
async def test_an_answer_that_dies_is_reported_as_the_answer_and_not_the_question():
    """
    The question is written down before the model is asked, so a model that dies afterwards has
    not lost it. Saying so is the whole point: this ending offers a fresh answer, while the one
    that means the question never landed offers to send it again. Reporting the wrong one is how
    people end up asking twice.
    """
    user = User(id="u1", full_name="Alex")
    agent = AgentPersonality(id="a1", name="Assistant", tool_permissions=[])
    session = ConversationSession(id="s1", user_id="u1", agent_id="a1")

    session_repo = FakeSessionRepository(sessions=[session])
    use_case = ProcessChatTurnUseCase(
        session_repo=session_repo,
        agent_repo=FakeAgentRepository(agents=[agent]),
        llm_client=ExplodingLLMClient(),
        context_assembler=FakeContextAssembler(),
        tool_executor=FakeToolExecutor(),
        tool_lister=FakeToolLister(),
        uow=FakeUnitOfWork(),
    )

    events = [
        ev
        async for ev in use_case.execute_stream(
            session_id="s1", current_user=user, content="What's left before Friday?"
        )
    ]

    assert events[-1]["type"] == "turn_failed"
    # The question is on the hub, which is what makes this the answer's failure and not its own.
    assert session_repo.messages[-1].content == "What's left before Friday?"


@pytest.mark.asyncio
async def test_regenerating_an_answer_that_dies_also_says_the_answer_failed():
    user = User(id="u1", full_name="Alex")
    agent = AgentPersonality(id="a1", name="Assistant", tool_permissions=[])
    session = ConversationSession(id="s1", user_id="u1", agent_id="a1")
    asked = ChatMessage(id="m1", session_id="s1", role="user", content="Plan meals")

    use_case = ProcessChatTurnUseCase(
        session_repo=FakeSessionRepository(sessions=[session], messages=[asked]),
        agent_repo=FakeAgentRepository(agents=[agent]),
        llm_client=ExplodingLLMClient(),
        context_assembler=FakeContextAssembler(),
        tool_executor=FakeToolExecutor(),
        tool_lister=FakeToolLister(),
        uow=FakeUnitOfWork(),
    )

    events = [
        ev async for ev in use_case.regenerate_stream(session_id="s1", current_user=user)
    ]

    assert events[-1]["type"] == "turn_failed"


@pytest.mark.asyncio
async def test_a_turn_that_never_opens_is_not_reported_as_a_failed_answer():
    """
    Nothing was written down, so there is no answer to ask for again. This one has to keep
    reaching the caller as an exception, which the hub reports as the other kind of failure.
    """
    user = User(id="u1", full_name="Alex")

    use_case = ProcessChatTurnUseCase(
        session_repo=FakeSessionRepository(sessions=[]),
        agent_repo=FakeAgentRepository(agents=[]),
        llm_client=FakeLLMClient(),
        context_assembler=FakeContextAssembler(),
        tool_executor=FakeToolExecutor(),
        tool_lister=FakeToolLister(),
        uow=FakeUnitOfWork(),
    )

    with pytest.raises(EntityNotFoundException):
        [
            ev
            async for ev in use_case.execute_stream(
                session_id="nowhere", current_user=user, content="Hello"
            )
        ]


def _use_case(session_repo, agent, llm_client, tool_executor=None):
    return ProcessChatTurnUseCase(
        session_repo=session_repo,
        agent_repo=FakeAgentRepository(agents=[agent]),
        llm_client=llm_client,
        context_assembler=FakeContextAssembler(),
        tool_executor=tool_executor or FakeToolExecutor(),
        tool_lister=FakeToolLister(),
        uow=FakeUnitOfWork(),
    )


@pytest.mark.asyncio
async def test_a_tool_decision_shows_its_words_as_they_come():
    """
    The decision used to be one blocking call, so an agent with tools answered in a single lump at
    the end - or not at all, when a thinking model took longer than the timeout to finish it.

    Streamed, anything the model writes before calling a tool reaches you as it is written, and it
    stays in the answer: that is a change from the blocking call, which kept it out.
    """
    user = User(id="u1", full_name="Alex")
    agent = AgentPersonality(id="a1", name="Assistant", tool_permissions=["calendar_read"])
    session_repo = FakeSessionRepository(sessions=[ConversationSession(id="s1", user_id="u1", agent_id="a1")])
    tc = LLMToolCall(id="c1", name="calendar_read", arguments={"date": "2026-09-23"})
    llm_client = FakeLLMClient(
        stream_chunks_list=[
            [
                LLMResponseChunk(delta_content="Let me "),
                LLMResponseChunk(delta_content="check. "),
                LLMResponseChunk(tool_calls=[tc], finish_reason="tool_calls"),
            ],
            [LLMResponseChunk(delta_content="Tomorrow is light.")],
        ]
    )

    events = [
        ev
        async for ev in _use_case(session_repo, agent, llm_client).execute_stream(
            session_id="s1", current_user=user, content="What is on tomorrow?"
        )
    ]

    types = [ev["type"] for ev in events]
    first_tool = types.index("tool_executing")
    assert [ev["content"] for ev in events[:first_tool] if ev["type"] == "delta"] == ["Let me ", "check. "]
    assert events[-1]["assistant_content"] == "Let me check. Tomorrow is light."


@pytest.mark.asyncio
async def test_a_write_the_decision_asks_for_still_waits_for_approval():
    user = User(id="u1", full_name="Alex")
    agent = AgentPersonality(id="a1", name="Assistant", tool_permissions=["calendar_write"])
    session_repo = FakeSessionRepository(sessions=[ConversationSession(id="s1", user_id="u1", agent_id="a1")])
    tc = LLMToolCall(id="c1", name="calendar_write", arguments={"title": "Dinner"})
    tool_executor = FakeToolExecutor()
    llm_client = FakeLLMClient(
        stream_chunks_list=[
            [LLMResponseChunk(tool_calls=[tc], finish_reason="tool_calls")],
            [LLMResponseChunk(delta_content="Shall I add it?")],
        ]
    )

    events = [
        ev
        async for ev in _use_case(session_repo, agent, llm_client, tool_executor).execute_stream(
            session_id="s1", current_user=user, content="Put dinner in the calendar"
        )
    ]

    proposals = [ev for ev in events if ev["type"] == "tool_call"]
    assert proposals and proposals[0]["data"]["status"] == "proposal_pending"
    assert tool_executor.executed_calls == []


@pytest.mark.asyncio
async def test_reasoning_is_shown_while_it_happens_and_never_kept():
    """
    What the model says to itself is worth watching and worth nothing afterwards. It goes to the
    phone as it arrives and never into the answer that is saved.
    """
    user = User(id="u1", full_name="Alex")
    agent = AgentPersonality(id="a1", name="Assistant", tool_permissions=[])
    session_repo = FakeSessionRepository(sessions=[ConversationSession(id="s1", user_id="u1", agent_id="a1")])
    llm_client = FakeLLMClient(
        stream_chunks_list=[
            [
                LLMResponseChunk(delta_reasoning="Okay, the user wants "),
                LLMResponseChunk(delta_reasoning="tomorrow."),
                LLMResponseChunk(delta_content="A light day."),
            ]
        ]
    )

    events = [
        ev
        async for ev in _use_case(session_repo, agent, llm_client).execute_stream(
            session_id="s1", current_user=user, content="What is on tomorrow?"
        )
    ]

    assert [ev["content"] for ev in events if ev["type"] == "reasoning"] == ["Okay, the user wants ", "tomorrow."]
    assert events[-1]["assistant_content"] == "A light day."
    assert session_repo.messages[-1].content == "A light day."


@pytest.mark.asyncio
async def test_reasoning_around_a_tool_is_shown_too():
    user = User(id="u1", full_name="Alex")
    agent = AgentPersonality(id="a1", name="Assistant", tool_permissions=["calendar_read"])
    session_repo = FakeSessionRepository(sessions=[ConversationSession(id="s1", user_id="u1", agent_id="a1")])
    tc = LLMToolCall(id="c1", name="calendar_read", arguments={"date": "2026-09-23"})
    llm_client = FakeLLMClient(
        stream_chunks_list=[
            [LLMResponseChunk(delta_reasoning="I need the calendar."), LLMResponseChunk(tool_calls=[tc])],
            [LLMResponseChunk(delta_reasoning="Two events."), LLMResponseChunk(delta_content="Two things.")],
        ]
    )

    events = [
        ev
        async for ev in _use_case(session_repo, agent, llm_client).execute_stream(
            session_id="s1", current_user=user, content="What is on tomorrow?"
        )
    ]

    assert [ev["content"] for ev in events if ev["type"] == "reasoning"] == ["I need the calendar.", "Two events."]
    assert events[-1]["assistant_content"] == "Two things."


@pytest.mark.asyncio
async def test_the_question_is_accepted_the_moment_it_is_saved():
    """
    The earliest honest thing the hub can say. After it, whatever breaks is the answer's problem,
    never the question's - which is what keeps the phone from offering to send it twice.
    """
    user = User(id="u1", full_name="Alex")
    agent = AgentPersonality(id="a1", name="Assistant", tool_permissions=[])
    session_repo = FakeSessionRepository(sessions=[ConversationSession(id="s1", user_id="u1", agent_id="a1")])
    llm_client = FakeLLMClient(stream_chunks_list=[[LLMResponseChunk(delta_content="Hi.")]])

    turn = _use_case(session_repo, agent, llm_client).execute_stream(
        session_id="s1", current_user=user, content="Hello"
    )
    first = await turn.__anext__()

    assert first == {"type": "accepted"}
    assert session_repo.messages[-1].content == "Hello"
    await turn.aclose()


@pytest.mark.asyncio
async def test_regenerating_is_accepted_straight_away():
    user = User(id="u1", full_name="Alex")
    agent = AgentPersonality(id="a1", name="Assistant", tool_permissions=[])
    session_repo = FakeSessionRepository(
        sessions=[ConversationSession(id="s1", user_id="u1", agent_id="a1")],
        messages=[ChatMessage(id="m1", session_id="s1", role="user", content="Plan meals")],
    )
    llm_client = FakeLLMClient(stream_chunks_list=[[LLMResponseChunk(delta_content="Monday: soup.")]])

    events = [
        ev
        async for ev in _use_case(session_repo, agent, llm_client).regenerate_stream(
            session_id="s1", current_user=user
        )
    ]

    assert events[0] == {"type": "accepted"}

