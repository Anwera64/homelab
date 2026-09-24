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
from app.domain.entities.llm_model import LLMModel
from app.domain.use_cases.chat.process_chat_turn import ProcessChatTurnUseCase
from app.domain.use_cases.models.resolve_agent_model import ResolveAgentModelUseCase


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

    async def get_messages_after(self, session_id: str, after_id=None, limit: int = 200):
        mine = [m for m in self.messages if m.session_id == session_id]
        ids = [m.id for m in mine]
        start = ids.index(after_id) + 1 if after_id in ids else 0
        return mine[start:][-limit:]


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


class FakeLLMModelRepository:
    def __init__(self, models=None):
        self.models = {m.id: m for m in (models or [])}

    async def get_default(self):
        return next((m for m in self.models.values() if m.is_default), None)

    async def get_by_id(self, model_id: str):
        return self.models.get(model_id)


def _house_resolver():
    return ResolveAgentModelUseCase(
        FakeLLMModelRepository([LLMModel(id="m1", provider_model="house-model", display_name="House", is_default=True)])
    )


class FakeContextAssembler:
    def __init__(self):
        self.summaries = []

    async def execute(self, user, agent, recent_messages, is_secret_session=False, is_turn_secret=False, history_summary=None):
        self.summaries.append(history_summary)
        return [
            LLMMessage(role="system", content="System instructions"),
            *[LLMMessage(role=m.role, content=m.content) for m in recent_messages],
        ]


class FakeToolExecutor:
    def __init__(self):
        self.executed_calls = []

    async def execute(self, tool_name, arguments, user_id, agent_tool_permissions, is_secret_mode=False, role="assistant", sources=None):
        self.executed_calls.append({"tool": tool_name, "args": arguments, "sources": sources})
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
    agent = AgentPersonality(id="a1", name="Assistant")
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
        model_resolver=_house_resolver(),
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
        model_resolver=_house_resolver(),
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
        model_resolver=_house_resolver(),
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
        model_resolver=_house_resolver(),
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
        model_resolver=_house_resolver(),
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
        model_resolver=_house_resolver(),
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
    # After a round of tools the model may still call more (#35): the answer is just a round in
    # which it chose not to.
    assert llm_client.stream_calls[1]["tools"] is not None

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
        model_resolver=_house_resolver(),
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
        model_resolver=_house_resolver(),
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
        model_resolver=_house_resolver(),
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
        model_resolver=_house_resolver(),
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
        model_resolver=_house_resolver(),
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
        model_resolver=_house_resolver(),
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
        model_resolver=_house_resolver(),
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
    # The stretch before the tool and the one after are paragraphs apart, not run together (#33).
    assert events[-1]["assistant_content"] == "Let me check.\n\nTomorrow is light."


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
    # The turn now waits on the member, so the model says so with no tools to try the write again.
    assert len(llm_client.stream_calls) == 2
    assert llm_client.stream_calls[1]["tools"] is None
    assert events[-1]["assistant_content"] == "Shall I add it?"


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



@pytest.mark.asyncio
async def test_a_turn_asks_the_model_the_household_default_resolves_to():
    """GIVEN an agent that follows the household default WHEN it answers THEN the model asked is the default's."""
    user = User(id="u1", full_name="Alex")
    agent = AgentPersonality(id="a1", name="Assistant")
    session = ConversationSession(id="s1", user_id="u1", agent_id="a1")
    llm_client = FakeLLMClient(responses=[LLMResponse(content="Hi")])

    await _use_case(FakeSessionRepository(sessions=[session]), agent, llm_client).execute(
        session_id="s1", current_user=user, content="Hello"
    )

    assert [call["model"] for call in llm_client.chat_calls] == ["house-model"]


@pytest.mark.asyncio
async def test_a_streamed_turn_asks_the_model_the_household_default_resolves_to():
    """GIVEN an agent that follows the household default WHEN it streams an answer THEN the model asked is the default's."""
    user = User(id="u1", full_name="Alex")
    agent = AgentPersonality(id="a1", name="Assistant")
    session = ConversationSession(id="s1", user_id="u1", agent_id="a1")
    llm_client = FakeLLMClient(stream_chunks_list=[[LLMResponseChunk(delta_content="Hi", finish_reason="stop")]])

    [ev async for ev in _use_case(FakeSessionRepository(sessions=[session]), agent, llm_client).execute_stream(
        session_id="s1", current_user=user, content="Hello"
    )]

    assert [call["model"] for call in llm_client.stream_calls] == ["house-model"]


class SteppedClock:
    """Reads the given instants in order, one per call: each stretch of thinking reads it twice."""

    def __init__(self, *instants: float):
        self._instants = list(instants)

    def __call__(self) -> float:
        return self._instants.pop(0)


class FailingToolExecutor(FakeToolExecutor):
    async def execute(self, tool_name, arguments, user_id, agent_tool_permissions, is_secret_mode=False, role="assistant", sources=None):
        self.executed_calls.append({"tool": tool_name, "args": arguments})
        return ToolExecutionResult(tool_name=tool_name, success=False, error="unreachable")


def _parts_use_case(session_repo, agent, llm_client, tool_executor=None, clock=None):
    use_case = _use_case(session_repo, agent, llm_client, tool_executor)
    if clock is not None:
        use_case.clock = clock
    return use_case


@pytest.mark.asyncio
async def test_GIVEN_text_a_tool_then_text_WHEN_answered_THEN_the_parts_keep_that_order():
    """
    Issue #33: an answer that wrote, used a tool, and wrote again was saved as one string, so the
    phone could not tell where the tool happened and the two stretches ran together.
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
            [LLMResponseChunk(delta_content="Tomorrow "), LLMResponseChunk(delta_content="is light.")],
        ]
    )

    events = [
        ev
        async for ev in _parts_use_case(session_repo, agent, llm_client).execute_stream(
            session_id="s1", current_user=user, content="What is on tomorrow?"
        )
    ]

    expected = [
        {"type": "text", "content": "Let me check. "},
        {"type": "tool", "tool": "calendar_read", "success": True},
        {"type": "text", "content": "Tomorrow is light."},
    ]
    done = events[-1]
    assert done["parts"] == expected
    assert session_repo.messages[-1].metadata_json["parts"] == expected
    assert done["assistant_content"] == "Let me check.\n\nTomorrow is light."
    assert session_repo.messages[-1].content == "Let me check.\n\nTomorrow is light."


@pytest.mark.asyncio
async def test_GIVEN_thinking_before_each_stretch_WHEN_answered_THEN_each_stretch_is_its_own_thought():
    user = User(id="u1", full_name="Alex")
    agent = AgentPersonality(id="a1", name="Assistant", tool_permissions=["calendar_read"])
    session_repo = FakeSessionRepository(sessions=[ConversationSession(id="s1", user_id="u1", agent_id="a1")])
    tc = LLMToolCall(id="c1", name="calendar_read", arguments={})
    llm_client = FakeLLMClient(
        stream_chunks_list=[
            [
                LLMResponseChunk(delta_reasoning="I need "),
                LLMResponseChunk(delta_reasoning="the calendar."),
                LLMResponseChunk(delta_content="Let me check."),
                LLMResponseChunk(tool_calls=[tc]),
            ],
            [LLMResponseChunk(delta_reasoning="Two events."), LLMResponseChunk(delta_content="Two things.")],
        ]
    )
    # Stretch one runs 0 -> 6.2 s, stretch two 100 -> 108.6 s.
    clock = SteppedClock(0.0, 6.2, 100.0, 108.6)

    events = [
        ev
        async for ev in _parts_use_case(session_repo, agent, llm_client, clock=clock).execute_stream(
            session_id="s1", current_user=user, content="What is on tomorrow?"
        )
    ]

    assert events[-1]["parts"] == [
        {"type": "thought", "seconds": 6},
        {"type": "text", "content": "Let me check."},
        {"type": "tool", "tool": "calendar_read", "success": True},
        {"type": "thought", "seconds": 9},
        {"type": "text", "content": "Two things."},
    ]


@pytest.mark.asyncio
async def test_GIVEN_thinking_that_ends_the_stream_WHEN_answered_THEN_it_is_a_thought_of_at_least_one_second():
    user = User(id="u1", full_name="Alex")
    agent = AgentPersonality(id="a1", name="Assistant", tool_permissions=[])
    session_repo = FakeSessionRepository(sessions=[ConversationSession(id="s1", user_id="u1", agent_id="a1")])
    llm_client = FakeLLMClient(
        stream_chunks_list=[[LLMResponseChunk(delta_content="Hi."), LLMResponseChunk(delta_reasoning="Done?")]]
    )

    events = [
        ev
        async for ev in _parts_use_case(session_repo, agent, llm_client, clock=SteppedClock(5.0, 5.1)).execute_stream(
            session_id="s1", current_user=user, content="Hello"
        )
    ]

    assert events[-1]["parts"] == [{"type": "text", "content": "Hi."}, {"type": "thought", "seconds": 1}]


@pytest.mark.asyncio
async def test_GIVEN_a_tool_that_fails_WHEN_answered_THEN_its_part_says_so():
    user = User(id="u1", full_name="Alex")
    agent = AgentPersonality(id="a1", name="Assistant", tool_permissions=["calendar_read"])
    session_repo = FakeSessionRepository(sessions=[ConversationSession(id="s1", user_id="u1", agent_id="a1")])
    tc = LLMToolCall(id="c1", name="calendar_read", arguments={})
    llm_client = FakeLLMClient(
        stream_chunks_list=[[LLMResponseChunk(tool_calls=[tc])], [LLMResponseChunk(delta_content="I could not look.")]]
    )

    events = [
        ev
        async for ev in _parts_use_case(session_repo, agent, llm_client, FailingToolExecutor()).execute_stream(
            session_id="s1", current_user=user, content="What is on tomorrow?"
        )
    ]

    assert events[-1]["parts"] == [
        {"type": "tool", "tool": "calendar_read", "success": False},
        {"type": "text", "content": "I could not look."},
    ]


@pytest.mark.asyncio
async def test_GIVEN_an_answer_with_no_tools_WHEN_answered_THEN_it_is_one_text_part_and_the_same_content():
    user = User(id="u1", full_name="Alex")
    agent = AgentPersonality(id="a1", name="Assistant", tool_permissions=[])
    session_repo = FakeSessionRepository(sessions=[ConversationSession(id="s1", user_id="u1", agent_id="a1")])
    llm_client = FakeLLMClient(
        stream_chunks_list=[[LLMResponseChunk(delta_content="A light "), LLMResponseChunk(delta_content="day. ")]]
    )

    events = [
        ev
        async for ev in _parts_use_case(session_repo, agent, llm_client).execute_stream(
            session_id="s1", current_user=user, content="What is on tomorrow?"
        )
    ]

    assert events[-1]["parts"] == [{"type": "text", "content": "A light day. "}]
    assert session_repo.messages[-1].content == "A light day. "


@pytest.mark.asyncio
async def test_GIVEN_a_model_that_asks_for_tools_twice_WHEN_answered_THEN_both_rounds_run_and_it_answers():
    """
    Issue #35: after one round of tools the model was asked for its answer with no tools left, so a
    model that wanted to search again said it would and stopped - the turn ended on a promise.
    """
    user = User(id="u1", full_name="Alex")
    agent = AgentPersonality(id="a1", name="Assistant", tool_permissions=["calendar_read"])
    session_repo = FakeSessionRepository(sessions=[ConversationSession(id="s1", user_id="u1", agent_id="a1")])
    first = LLMToolCall(id="c1", name="calendar_read", arguments={"date": "2026-09-24"})
    second = LLMToolCall(id="c2", name="calendar_read", arguments={"date": "2026-09-25"})
    tool_executor = FakeToolExecutor()
    llm_client = FakeLLMClient(
        stream_chunks_list=[
            [LLMResponseChunk(delta_content="Let me look. "), LLMResponseChunk(tool_calls=[first])],
            [LLMResponseChunk(delta_content="And the day after. "), LLMResponseChunk(tool_calls=[second])],
            [LLMResponseChunk(delta_content="Both days are free.")],
        ]
    )

    events = [
        ev
        async for ev in _use_case(session_repo, agent, llm_client, tool_executor).execute_stream(
            session_id="s1", current_user=user, content="Am I free today and tomorrow?"
        )
    ]

    assert [call["args"] for call in tool_executor.executed_calls] == [first.arguments, second.arguments]
    assert len(llm_client.stream_calls) == 3
    assert all(call["tools"] is not None for call in llm_client.stream_calls)
    expected_parts = [
        {"type": "text", "content": "Let me look. "},
        {"type": "tool", "tool": "calendar_read", "success": True},
        {"type": "text", "content": "And the day after. "},
        {"type": "tool", "tool": "calendar_read", "success": True},
        {"type": "text", "content": "Both days are free."},
    ]
    expected_content = "Let me look.\n\nAnd the day after.\n\nBoth days are free."
    done = events[-1]
    assert done["parts"] == expected_parts
    assert done["assistant_content"] == expected_content
    assert session_repo.messages[-1].metadata_json["parts"] == expected_parts
    assert session_repo.messages[-1].content == expected_content


@pytest.mark.asyncio
async def test_GIVEN_a_model_that_never_stops_calling_tools_WHEN_the_budget_runs_out_THEN_it_is_made_to_answer():
    user = User(id="u1", full_name="Alex")
    agent = AgentPersonality(id="a1", name="Assistant", tool_permissions=["calendar_read"])
    session_repo = FakeSessionRepository(sessions=[ConversationSession(id="s1", user_id="u1", agent_id="a1")])
    tool_executor = FakeToolExecutor()
    llm_client = FakeLLMClient(
        stream_chunks_list=[
            [LLMResponseChunk(tool_calls=[LLMToolCall(id="c1", name="calendar_read", arguments={})])],
            [LLMResponseChunk(tool_calls=[LLMToolCall(id="c2", name="calendar_read", arguments={})])],
            [LLMResponseChunk(delta_content="Here is what I found.")],
        ]
    )
    use_case = _use_case(session_repo, agent, llm_client, tool_executor)
    use_case.max_iterations = 3

    events = [
        ev
        async for ev in use_case.execute_stream(session_id="s1", current_user=user, content="Look everything up")
    ]

    assert len(tool_executor.executed_calls) == 2
    assert len(llm_client.stream_calls) == 3
    last_call = llm_client.stream_calls[-1]
    assert last_call["tools"] is None
    assert last_call["messages"][-1].role == "system"
    assert last_call["messages"][-1].content.startswith("Tool budget reached")
    assert events[-1]["assistant_content"] == "Here is what I found."
    assert session_repo.messages[-1].content == "Here is what I found."


@pytest.mark.asyncio
async def test_GIVEN_two_rounds_of_tools_WHEN_answered_without_streaming_THEN_both_rounds_run():
    user = User(id="u1", full_name="Alex")
    agent = AgentPersonality(id="a1", name="Assistant", tool_permissions=["calendar_read"])
    session_repo = FakeSessionRepository(sessions=[ConversationSession(id="s1", user_id="u1", agent_id="a1")])
    tool_executor = FakeToolExecutor()
    llm_client = FakeLLMClient(
        responses=[
            LLMResponse(content="", tool_calls=[LLMToolCall(id="c1", name="calendar_read", arguments={})]),
            LLMResponse(content="", tool_calls=[LLMToolCall(id="c2", name="calendar_read", arguments={})]),
            LLMResponse(content="Both days are free."),
        ]
    )

    result = await _use_case(session_repo, agent, llm_client, tool_executor).execute(
        session_id="s1", current_user=user, content="Am I free today and tomorrow?"
    )

    assert len(tool_executor.executed_calls) == 2
    assert result.message.content == "Both days are free."


class ResearchToolLister:
    async def execute(self):
        return [
            ToolDefinition(name=name, description=name, parameters_schema={"type": "object"})
            for name in ("searxng_search", "read_page", "lookup_sources", "calendar_read")
        ]


class CountingIndexFactory:
    def __init__(self):
        self.made = []

    def new(self):
        index = object()
        self.made.append(index)
        return index


def _research_use_case(session_repo, agent, llm_client, tool_executor, factory):
    use_case = _use_case(session_repo, agent, llm_client, tool_executor)
    use_case.tool_lister = ResearchToolLister()
    use_case.source_index_factory = factory
    return use_case


@pytest.mark.asyncio
async def test_GIVEN_an_agent_that_may_search_WHEN_tools_are_offered_THEN_reading_and_looking_up_come_with_it():
    user = User(id="u1", full_name="Alex")
    agent = AgentPersonality(id="a1", name="Researcher", tool_permissions=["searxng_search"])
    session_repo = FakeSessionRepository(sessions=[ConversationSession(id="s1", user_id="u1", agent_id="a1")])
    llm_client = FakeLLMClient(stream_chunks_list=[[LLMResponseChunk(delta_content="Hi.")]])

    [ev async for ev in _research_use_case(
        session_repo, agent, llm_client, FakeToolExecutor(), CountingIndexFactory()
    ).execute_stream(session_id="s1", current_user=user, content="Hello")]

    offered = [tool["function"]["name"] for tool in llm_client.stream_calls[0]["tools"]]
    assert offered == ["searxng_search", "read_page", "lookup_sources"]


@pytest.mark.asyncio
async def test_GIVEN_tools_in_two_rounds_WHEN_answered_THEN_every_call_shares_one_turns_sources_and_the_next_turn_gets_new_ones():
    user = User(id="u1", full_name="Alex")
    agent = AgentPersonality(id="a1", name="Researcher", tool_permissions=["searxng_search"])
    session_repo = FakeSessionRepository(sessions=[ConversationSession(id="s1", user_id="u1", agent_id="a1")])
    search = LLMToolCall(id="c1", name="searxng_search", arguments={"query": "tariffs"})
    lookup = LLMToolCall(id="c2", name="lookup_sources", arguments={"question": "tariffs"})
    llm_client = FakeLLMClient(
        stream_chunks_list=[
            [LLMResponseChunk(tool_calls=[search])],
            [LLMResponseChunk(tool_calls=[lookup])],
            [LLMResponseChunk(delta_content="Tariffs rose.")],
            [LLMResponseChunk(delta_content="Second answer.")],
        ]
    )
    tool_executor = FakeToolExecutor()
    factory = CountingIndexFactory()
    use_case = _research_use_case(session_repo, agent, llm_client, tool_executor, factory)

    [ev async for ev in use_case.execute_stream(session_id="s1", current_user=user, content="Tariffs?")]
    [ev async for ev in use_case.execute_stream(session_id="s1", current_user=user, content="Thanks")]

    first, second = (call["sources"] for call in tool_executor.executed_calls)
    assert first is not None and first is second
    assert len(factory.made) == 2


class PassagesToolExecutor(FakeToolExecutor):
    """Looks up three passages of 900 characters each: about 950 tokens by the hub's estimate."""

    async def execute(self, tool_name, arguments, user_id, agent_tool_permissions, is_secret_mode=False, role="assistant", sources=None):
        self.executed_calls.append({"tool": tool_name, "args": arguments, "sources": sources})
        return ToolExecutionResult(
            tool_name=tool_name,
            success=True,
            data={"passages": [{"id": f"p1.{i}", "text": str(i) * 900} for i in (1, 2, 3)]},
        )


def _windowed_use_case(session_repo, agent, llm_client, window, reserve):
    use_case = _use_case(session_repo, agent, llm_client, PassagesToolExecutor())
    use_case.context_window_tokens = window
    use_case.answer_reserve_tokens = reserve
    return use_case


def _tool_message(call) -> dict:
    import json
    return json.loads([m for m in call["messages"] if m.role == "tool"][-1].content)


@pytest.mark.asyncio
async def test_GIVEN_a_result_bigger_than_the_room_left_WHEN_answered_THEN_whole_items_are_kept_and_the_answer_is_forced():
    user = User(id="u1", full_name="Alex")
    agent = AgentPersonality(id="a1", name="Assistant", tool_permissions=["calendar_read"])
    session_repo = FakeSessionRepository(sessions=[ConversationSession(id="s1", user_id="u1", agent_id="a1")])
    call = LLMToolCall(id="c1", name="calendar_read", arguments={})
    llm_client = FakeLLMClient(
        stream_chunks_list=[[LLMResponseChunk(tool_calls=[call])], [LLMResponseChunk(delta_content="From two passages.")]]
    )

    events = [ev async for ev in _windowed_use_case(session_repo, agent, llm_client, window=1200, reserve=300)
              .execute_stream(session_id="s1", current_user=user, content="What do the sources say?")]

    seen = _tool_message(llm_client.stream_calls[1])
    # Whole passages only: two fit, the third is left out and the model is told.
    assert [p["text"] for p in seen["passages"]] == ["1" * 900, "2" * 900]
    assert seen["left_out"] == 1
    # Too little room for another round, so the second call is the answer.
    assert llm_client.stream_calls[1]["tools"] is None
    assert llm_client.stream_calls[1]["messages"][-1].content.startswith("Tool budget reached")
    assert events[-1]["assistant_content"] == "From two passages."


@pytest.mark.asyncio
async def test_GIVEN_no_room_for_even_one_item_WHEN_a_tool_returns_THEN_the_model_is_told_to_answer_with_what_it_has():
    user = User(id="u1", full_name="Alex")
    agent = AgentPersonality(id="a1", name="Assistant", tool_permissions=["calendar_read"])
    session_repo = FakeSessionRepository(sessions=[ConversationSession(id="s1", user_id="u1", agent_id="a1")])
    call = LLMToolCall(id="c1", name="calendar_read", arguments={})
    llm_client = FakeLLMClient(
        stream_chunks_list=[[LLMResponseChunk(tool_calls=[call])], [LLMResponseChunk(delta_content="What I have.")]]
    )

    [ev async for ev in _windowed_use_case(session_repo, agent, llm_client, window=650, reserve=300)
     .execute_stream(session_id="s1", current_user=user, content="What do the sources say?")]

    assert _tool_message(llm_client.stream_calls[1]) == {
        "error": "No room left for more results; answer with what you have."
    }
    assert llm_client.stream_calls[1]["tools"] is None


@pytest.mark.asyncio
async def test_GIVEN_the_answer_is_forced_WHEN_the_model_is_asked_THEN_the_question_is_the_last_thing_it_reads():
    user = User(id="u1", full_name="Alex")
    agent = AgentPersonality(id="a1", name="Assistant", tool_permissions=["calendar_read"])
    session_repo = FakeSessionRepository(sessions=[ConversationSession(id="s1", user_id="u1", agent_id="a1")])
    llm_client = FakeLLMClient(
        stream_chunks_list=[
            [LLMResponseChunk(tool_calls=[LLMToolCall(id="c1", name="calendar_read", arguments={})])],
            [LLMResponseChunk(delta_content="Answer.")],
        ]
    )
    use_case = _use_case(session_repo, agent, llm_client)
    use_case.max_iterations = 2

    [ev async for ev in use_case.execute_stream(session_id="s1", current_user=user, content="Rank them by gravity")]

    last = llm_client.stream_calls[-1]["messages"][-1]
    assert last.role == "system"
    assert last.content.startswith("Tool budget reached")
    assert "Rank them by gravity" in last.content


@pytest.mark.asyncio
async def test_GIVEN_an_answer_the_window_cut_off_WHEN_saved_THEN_it_is_flagged_as_cut_off():
    """It used to be saved as a finished answer ending mid-sentence, with nothing saying why."""
    user = User(id="u1", full_name="Alex")
    agent = AgentPersonality(id="a1", name="Assistant")
    session_repo = FakeSessionRepository(sessions=[ConversationSession(id="s1", user_id="u1", agent_id="a1")])
    llm_client = FakeLLMClient(
        stream_chunks_list=[[LLMResponseChunk(delta_content="Let me compile"), LLMResponseChunk(finish_reason="length")]]
    )

    events = [ev async for ev in _use_case(session_repo, agent, llm_client)
              .execute_stream(session_id="s1", current_user=user, content="Rank them")]

    assert events[-1]["cut_off"] is True
    assert session_repo.messages[-1].metadata_json["cut_off"] is True


@pytest.mark.asyncio
async def test_GIVEN_an_answer_that_finished_WHEN_saved_THEN_it_is_not_flagged():
    user = User(id="u1", full_name="Alex")
    agent = AgentPersonality(id="a1", name="Assistant")
    session_repo = FakeSessionRepository(sessions=[ConversationSession(id="s1", user_id="u1", agent_id="a1")])
    llm_client = FakeLLMClient(
        stream_chunks_list=[[LLMResponseChunk(delta_content="Done."), LLMResponseChunk(finish_reason="stop")]]
    )

    events = [ev async for ev in _use_case(session_repo, agent, llm_client)
              .execute_stream(session_id="s1", current_user=user, content="Hi")]

    assert events[-1]["cut_off"] is False
    assert "cut_off" not in session_repo.messages[-1].metadata_json



@pytest.mark.asyncio
async def test_GIVEN_a_chat_with_a_summary_WHEN_streamed_THEN_the_model_gets_the_summary_and_only_the_messages_after_it():
    user = User(id="u1", full_name="Alex")
    agent = AgentPersonality(id="a1", name="Assistant")
    session = ConversationSession(
        id="s1", user_id="u1", agent_id="a1", history_summary="Alex planned a trip to Lima.", summarized_through_id="m2"
    )
    earlier = [
        ChatMessage(id=f"m{i}", session_id="s1", role="user" if i % 2 else "assistant", content=f"message {i}")
        for i in range(1, 5)
    ]
    session_repo = FakeSessionRepository(sessions=[session], messages=earlier)
    llm_client = FakeLLMClient(stream_chunks_list=[[LLMResponseChunk(delta_content="Sure.")]])
    assembler = FakeContextAssembler()
    use_case = _use_case(session_repo, agent, llm_client)
    use_case.context_assembler = assembler

    [ev async for ev in use_case.execute_stream(session_id="s1", current_user=user, content="And the hotel?")]

    assert assembler.summaries == ["Alex planned a trip to Lima."]
    sent = [m.content for m in llm_client.stream_calls[0]["messages"][1:]]
    assert sent == ["message 3", "message 4", "And the hotel?"]


class StrictToolExecutor(FakeToolExecutor):
    """Refuses a tool the agent wasn't given, the way ExecuteToolUseCase does: by raising."""

    async def execute(self, tool_name, arguments, user_id, agent_tool_permissions, is_secret_mode=False, role="assistant", sources=None):
        from app.domain.exceptions import ToolPermissionDeniedException

        if tool_name not in agent_tool_permissions:
            raise ToolPermissionDeniedException(f"Agent does not have permission to execute tool '{tool_name}'.")
        return await super().execute(tool_name, arguments, user_id, agent_tool_permissions, is_secret_mode, role, sources)


async def _turn_with_a_bad_tool_name(bad_name: str):
    import json

    user = User(id="u1", full_name="Alex")
    agent = AgentPersonality(id="a1", name="Researcher", tool_permissions=["calendar_read"])
    session_repo = FakeSessionRepository(sessions=[ConversationSession(id="s1", user_id="u1", agent_id="a1")])
    llm_client = FakeLLMClient(
        stream_chunks_list=[
            [LLMResponseChunk(tool_calls=[LLMToolCall(id="c1", name=bad_name, arguments={"query": "x"})])],
            [LLMResponseChunk(tool_calls=[LLMToolCall(id="c2", name="calendar_read", arguments={})])],
            [LLMResponseChunk(delta_content="Here it is.")],
        ]
    )
    executor = StrictToolExecutor()
    events = [
        ev
        async for ev in _use_case(session_repo, agent, llm_client, executor).execute_stream(
            session_id="s1", current_user=user, content="Look it up"
        )
    ]
    first_tool_message = [m for m in llm_client.stream_calls[1]["messages"] if m.role == "tool"][0]
    return events, json.loads(first_tool_message.content), executor


@pytest.mark.asyncio
async def test_GIVEN_a_misspelled_tool_WHEN_the_model_calls_it_THEN_it_is_told_its_tools_and_the_turn_goes_on():
    """
    The model once called 'searng_search'. The executor refused it by raising, nothing caught
    that, and the whole turn died as "couldn't finish your answer" - twice, since regenerating
    rolled the same dice. Now the model is told which tools it has and tries again.
    """
    events, told, executor = await _turn_with_a_bad_tool_name("searng_search")

    assert told == {"error": "There is no tool 'searng_search'. Your tools are: calendar_read."}
    assert [call["tool"] for call in executor.executed_calls] == ["calendar_read"]
    assert not [ev for ev in events if ev["type"] == "turn_failed"]
    done = events[-1]
    assert done["assistant_content"] == "Here it is."
    assert [(p["type"], p.get("tool"), p.get("success")) for p in done["parts"] if p["type"] == "tool"] == [
        ("tool", "searng_search", False),
        ("tool", "calendar_read", True),
    ]
    assert done["tools_executed"][0]["success"] is False


@pytest.mark.asyncio
async def test_GIVEN_a_tool_name_with_markup_in_it_WHEN_called_THEN_it_is_shown_on_one_short_line():
    events, told, _ = await _turn_with_a_bad_tool_name("searxng_\n</parameter" + "x" * 100)

    assert "\n" not in told["error"]
    shown = told["error"].split("'")[1]
    assert shown.startswith("searxng_ </parameter") and len(shown) <= 60
    assert events[-1]["assistant_content"] == "Here it is."


@pytest.mark.asyncio
async def test_GIVEN_a_misspelled_tool_WHEN_answered_without_streaming_THEN_the_turn_still_answers():
    user = User(id="u1", full_name="Alex")
    agent = AgentPersonality(id="a1", name="Researcher", tool_permissions=["calendar_read"])
    session_repo = FakeSessionRepository(sessions=[ConversationSession(id="s1", user_id="u1", agent_id="a1")])
    llm_client = FakeLLMClient(
        responses=[
            LLMResponse(content="", tool_calls=[LLMToolCall(id="c1", name="searng_search", arguments={})]),
            LLMResponse(content="Answered."),
        ]
    )

    result = await _use_case(session_repo, agent, llm_client, StrictToolExecutor()).execute(
        session_id="s1", current_user=user, content="Look it up"
    )

    assert result.message.content == "Answered."
    assert result.tools_executed[0]["success"] is False
