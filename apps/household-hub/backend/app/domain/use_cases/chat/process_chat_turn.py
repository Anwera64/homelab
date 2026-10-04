import json
import logging
import re
import time
from dataclasses import dataclass, field
from typing import Any, Callable, Dict, FrozenSet, List, Optional, Tuple

from app.domain.entities.user import User
from app.domain.entities.session import ChatMessage, ConversationSession
from app.domain.entities.llm_message import LLMMessage, LLMToolCall
from app.domain.entities.tool_definition import ToolExecutionResult
from app.domain.repositories.session_repository import ISessionRepository
from app.domain.repositories.agent_repository import IAgentRepository
from app.domain.repositories.llm_client import ILLMClient
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.repositories.tool_approval_repository import IToolApprovalRepository
from app.domain.use_cases.chat.assemble_agent_context import AssembleAgentContextUseCase
from app.domain.repositories.source_index import ISourceIndexFactory
from app.domain.use_cases.integrations.execute_tool import ExecuteToolUseCase, effective_tool_permissions
from app.domain.use_cases.integrations.list_available_tools import ListAvailableToolsUseCase
from app.domain.use_cases.chat.tool_summary import WRITE_ACTIONS, describe_write, summarize_tool
from app.domain.use_cases.chat.tool_approval_settings import auto_approved
from app.domain.use_cases.chat.tool_approval import (
    DECLINED,
    PAUSED_TURN,
    card_to_decide,
    dump_messages,
    load_messages,
    member_changes,
    needs_asking,
    proposal_event,
    proposal_part,
    told_with_edit,
)
from app.domain.use_cases.integrations.turn_sources import TurnSources
from app.domain.use_cases.chat.token_estimate import estimate_tokens
from app.domain.use_cases.models.resolve_agent_model import ResolveAgentModelUseCase
from app.domain.exceptions import (
    EntityNotFoundException,
    ZeroLeakViolationException,
    InvalidOperationException,
    ToolPermissionDeniedException,
)


logger = logging.getLogger(__name__)

# How much of a call's arguments the log keeps: enough to see what was asked, not a whole note.
LOGGED_ARGUMENTS = 500


def _log_call(tc: LLMToolCall, result: ToolExecutionResult, is_turn_secret: bool) -> None:
    """
    Says in the hub's log what a tool was asked, and why it failed if it did. The phone is only shown
    that a step ran, so without this neither a failure nor a step that did the wrong thing can be
    read back afterwards. What the tool gave back is not logged: a calendar read is the member's
    week. A secret turn leaves out what was asked too: its content must not outlive it in a log.
    """
    name = " ".join(tc.name.split())[:60]
    asked = "left out (secret turn)" if is_turn_secret else json.dumps(tc.arguments, default=str)[:LOGGED_ARGUMENTS]
    if result.success:
        logger.info("Tool %s ok | arguments: %s", name, asked)
        return
    reason = getattr(result, "reason", None)
    logger.warning(
        "Tool %s failed: %s | reason: %s | arguments: %s",
        name,
        result.error,
        getattr(reason, "value", reason) or "none",
        asked,
    )


@dataclass
class ChatTurnResult:
    message: ChatMessage
    tools_executed: List[Dict[str, Any]] = field(default_factory=list)
    is_secret: bool = False
    privacy_trigger_detected: bool = False
    suggest_secret_mode: bool = False
    is_turn_secret: bool = False


class _AnswerParts:
    """
    What an answer did, in the order it did it: stretches of text, of thinking, and the tools
    between them.

    The answer used to be saved as one string, with its tools in a separate list, so nothing
    recorded where in the text a tool had run: the phone drew every tool above the whole answer
    and ran the stretches either side of it together (#33). Thinking is kept as how long it took,
    never what was thought.
    """

    def __init__(self, clock: Callable[[], float], parts: Optional[List[Dict[str, Any]]] = None) -> None:
        self._clock = clock
        self._thinking_since: Optional[float] = None
        # A paused answer carries on from the parts it saved.
        self.parts: List[Dict[str, Any]] = list(parts or [])

    def thinking(self) -> None:
        if self._thinking_since is None:
            self._thinking_since = self._clock()

    def text(self, content: str) -> None:
        self.stop_thinking()
        if self.parts and self.parts[-1]["type"] == "text":
            self.parts[-1]["content"] += content
        else:
            self.parts.append({"type": "text", "content": content})

    def tool(
        self,
        name: str,
        success: bool,
        summary: Optional[Dict[str, Any]] = None,
        at: Optional[int] = None,
        auto: bool = False,
    ) -> None:
        """
        A tool used, with what it shows on the phone when it has anything to show (#40). [at] puts
        an approved write where its card was, rather than after everything since. [auto] marks a
        write that ran without asking, because the member made it automatic.
        """
        self.stop_thinking()
        part: Dict[str, Any] = {"type": "tool", "tool": name, "success": success}
        if summary is not None:
            part["summary"] = summary
        if auto:
            part["auto"] = True
        if at is None:
            self.parts.append(part)
        else:
            self.parts[at] = part

    def stop_thinking(self) -> None:
        """Ends a stretch of thinking: whole seconds, rounded half up, and never zero."""
        if self._thinking_since is None:
            return
        elapsed = self._clock() - self._thinking_since
        self._thinking_since = None
        self.parts.append({"type": "thought", "seconds": max(1, int(elapsed + 0.5))})

    def content(self) -> str:
        """
        The answer as plain text: its stretches, with a paragraph between two that a tool came
        between, so what was written before a tool and after it do not run together.
        """
        written = ""
        tool_since_text = False
        for part in self.parts:
            if part["type"] in ("tool", "proposal", "declined"):
                tool_since_text = True
            elif part["type"] == "text":
                if written and tool_since_text:
                    written = written.rstrip() + "\n\n" + part["content"].lstrip()
                else:
                    written += part["content"]
                tool_since_text = False
        return written


class _ContextBudget:
    """
    How much of the model's window a turn has used, so it always leaves room for the answer.

    Research used to fill the window until the answer itself was cut off mid-sentence (#35). The
    budget keeps [answer_reserve_tokens] free: a round only starts when there is room for it, and a
    tool result that does not fit keeps whole items only - whole passages, whole events - and says
    how many it left out. Text is never cut mid-way; when not even one item fits, the model is told
    to answer with what it has.

    Tokens are estimated from characters (see token_estimate). With no window set there is no limit.
    """

    # Less room than this and another round would only crowd the answer.
    SMALLEST_ROUND_TOKENS = 256
    NO_ROOM = "No room left for more results; answer with what you have."

    def __init__(self, window_tokens: Optional[int], answer_reserve_tokens: int):
        self.window_tokens = window_tokens
        self.answer_reserve_tokens = answer_reserve_tokens

    def room(self, messages: List[LLMMessage], tools: Optional[List[Dict[str, Any]]]) -> float:
        if self.window_tokens is None:
            return float("inf")
        return self.window_tokens - self.answer_reserve_tokens - self._tokens(messages, tools)

    def has_room_for_a_round(self, messages: List[LLMMessage], tools: Optional[List[Dict[str, Any]]]) -> bool:
        return self.room(messages, tools) >= self.SMALLEST_ROUND_TOKENS

    def fit(
        self, result: Dict[str, Any], messages: List[LLMMessage], tools: Optional[List[Dict[str, Any]]]
    ) -> Tuple[str, bool]:
        """The result as the model will read it, and whether the turn has run out of room."""
        room = self.room(messages, tools)
        whole = json.dumps(result)
        if estimate_tokens(whole) <= room:
            return whole, False
        items_key = next((key for key, value in result.items() if isinstance(value, list) and value), None)
        if items_key is not None:
            items = result[items_key]
            for keep in range(len(items) - 1, 0, -1):
                trimmed = json.dumps({**result, items_key: items[:keep], "left_out": len(items) - keep})
                if estimate_tokens(trimmed) <= room:
                    return trimmed, False
        return json.dumps({"error": self.NO_ROOM}), True

    def _tokens(self, messages: List[LLMMessage], tools: Optional[List[Dict[str, Any]]]) -> float:
        tokens = estimate_tokens(json.dumps(tools)) if tools else 0.0
        for message in messages:
            tokens += estimate_tokens(message.content)
            for call in message.tool_calls or []:
                tokens += estimate_tokens(call.name + json.dumps(call.arguments))
        return tokens


class ProcessChatTurnUseCase:
    PRIVACY_TRIGGERS = [
        r"\bkeep this between us\b",
        r"\bdon'?t tell\b",
        r"\bthis is a secret\b",
        r"\bkeep this secret\b",
        r"\bbetween you and me\b",
        r"\bdon'?t share this\b",
        r"\bkeep it confidential\b",
    ]

    def __init__(
        self,
        session_repo: ISessionRepository,
        agent_repo: IAgentRepository,
        llm_client: ILLMClient,
        context_assembler: AssembleAgentContextUseCase,
        tool_executor: ExecuteToolUseCase,
        tool_lister: ListAvailableToolsUseCase,
        uow: IUnitOfWork,
        model_resolver: ResolveAgentModelUseCase,
        max_iterations: int = 5,
        clock: Callable[[], float] = time.monotonic,
        source_index_factory: Optional[ISourceIndexFactory] = None,
        context_window_tokens: Optional[int] = None,
        answer_reserve_tokens: int = 4096,
        approval_repo: Optional[IToolApprovalRepository] = None,
    ):
        self.session_repo = session_repo
        self.agent_repo = agent_repo
        self.llm_client = llm_client
        self.context_assembler = context_assembler
        self.tool_executor = tool_executor
        self.tool_lister = tool_lister
        self.uow = uow
        self.model_resolver = model_resolver
        self.max_iterations = max_iterations
        # Times each stretch of thinking for the answer's parts. Injected so a test can move it.
        self.clock = clock
        # One index per streamed turn: what a turn searched and read, for its tools to look up.
        self.source_index_factory = source_index_factory
        # The model's window, and what a turn keeps free in it for the answer. No window, no limit.
        self.context_window_tokens = context_window_tokens
        self.answer_reserve_tokens = answer_reserve_tokens
        # Which writes each member lets agents do without asking. None: every write asks.
        self.approval_repo = approval_repo

    def _check_privacy_triggers(self, text: str) -> bool:
        lower_text = text.lower()
        for pattern in self.PRIVACY_TRIGGERS:
            if re.search(pattern, lower_text):
                return True
        return False

    async def execute(
        self,
        session_id: str,
        current_user: User,
        content: str,
        timezone_name: Optional[str] = None,
    ) -> ChatTurnResult:
        session = await self.session_repo.get_by_id(session_id)
        if not session:
            raise EntityNotFoundException("Session not found")

        if session.user_id != current_user.id:
            raise ZeroLeakViolationException(
                "Zero-Leak Privacy violation: You cannot chat in another member's session."
            )

        if session.is_archived or session.agent_id is None:
            raise InvalidOperationException("Cannot send messages to an archived conversation session.")

        agent = await self.agent_repo.get_by_id(session.agent_id)
        if not agent:
            raise EntityNotFoundException("Agent personality not found")

        if agent.deleted_at is not None:
            raise InvalidOperationException(
                "Cannot send messages while the agent is in trash. Restore the agent to continue chatting."
            )

        if not agent.is_active:
            raise InvalidOperationException(
                f"Agent '{agent.name}' is deactivated and cannot accept new messages."
            )

        model = await self.model_resolver.for_agent(agent)

        # 1. Natural Language Privacy Guard
        privacy_trigger_detected = self._check_privacy_triggers(content)
        is_turn_secret = privacy_trigger_detected or session.is_secret
        suggest_secret_mode = privacy_trigger_detected and not session.is_secret

        # 2. Persist User Message
        async with self.uow:
            user_msg = ChatMessage(
                session_id=session.id,
                role="user",
                content=content,
                metadata_json={"privacy_trigger_detected": privacy_trigger_detected},
            )
            await self.session_repo.add_message(user_msg)
            session.touch()
            await self.session_repo.update(session)
            await self.uow.commit()

        # 3. Assemble Prompt & Context
        recent_messages = await self.session_repo.get_messages_after(session.id, session.summarized_through_id)
        llm_messages = await self.context_assembler.execute(
            user=current_user,
            agent=agent,
            recent_messages=recent_messages,
            is_secret_session=session.is_secret,
            is_turn_secret=is_turn_secret,
            timezone_name=timezone_name,
            history_summary=session.history_summary,
        )

        # 4. Resolve Tool Schemas for Agent
        tool_defs_res = self.tool_lister.execute()
        available_tools_defs = (
            await tool_defs_res if hasattr(tool_defs_res, "__await__") else tool_defs_res
        )
        agent_tools = []
        if agent.tool_permissions:
            for td in available_tools_defs:
                if td.name in agent.tool_permissions:
                    agent_tools.append(
                        {
                            "type": "function",
                            "function": {
                                "name": td.name,
                                "description": td.description,
                                "parameters": td.parameters_schema,
                            },
                        }
                    )

        # 5. Multi-Turn Inference & Tool Loop
        auto = await auto_approved(self.approval_repo, current_user.id)
        tools_executed: List[Dict[str, Any]] = []
        final_content = ""
        iterations = 0

        while iterations < self.max_iterations:
            iterations += 1

            if iterations == self.max_iterations:
                # Force final synthesis if budget limit reached
                llm_messages.append(
                    LLMMessage(
                        role="system",
                        content="Tool budget reached. Please synthesize the findings gathered so far and provide your final response to the user.",
                    )
                )
                final_resp = await self.llm_client.chat_completion(
                    messages=llm_messages,
                    model=model,
                    temperature=agent.temperature,
                    top_p=agent.top_p,
                    tools=None,
                )
                final_content = final_resp.content
                break

            resp = await self.llm_client.chat_completion(
                messages=llm_messages,
                model=model,
                temperature=agent.temperature,
                top_p=agent.top_p,
                tools=agent_tools if agent_tools else None,
            )

            if not resp.tool_calls:
                final_content = resp.content
                break

            # Process tool calls
            llm_messages.append(
                LLMMessage(role="assistant", content=resp.content, tool_calls=resp.tool_calls)
            )

            for tc in resp.tool_calls:
                # Write tool confirmation policy
                if needs_asking(tc, auto, is_turn_secret):
                    proposal_info = {
                        "tool": tc.name,
                        "status": "proposal_pending",
                        "arguments": tc.arguments,
                        "message": f"Action '{tc.name}' requires member confirmation.",
                    }
                    tools_executed.append(proposal_info)
                    llm_messages.append(
                        LLMMessage(
                            role="tool",
                            tool_call_id=tc.id,
                            name=tc.name,
                            content=json.dumps(proposal_info),
                        )
                    )
                else:
                    # Execute tool
                    tool_result = await self._run_tool(
                        tc, agent, current_user.id, is_turn_secret, sources=None, offered=agent_tools
                    )
                    exec_info = {
                        "tool": tc.name,
                        "success": tool_result.success,
                        "arguments": tc.arguments,
                        "data": tool_result.data,
                        "error": tool_result.error,
                    }
                    if tc.name in WRITE_ACTIONS and not is_turn_secret:
                        exec_info["auto"] = True
                    tools_executed.append(exec_info)
                    llm_messages.append(
                        LLMMessage(
                            role="tool",
                            tool_call_id=tc.id,
                            name=tc.name,
                            content=json.dumps(
                                tool_result.data if tool_result.success else {"error": tool_result.error}
                            ),
                        )
                    )

        # 6. Persist Assistant Reply
        async with self.uow:
            asst_msg = ChatMessage(
                session_id=session.id,
                role="assistant",
                content=final_content,
                metadata_json={
                    "tools_executed": tools_executed,
                    "privacy_trigger_detected": privacy_trigger_detected,
                    "suggest_secret_mode": suggest_secret_mode,
                },
            )
            created_asst_msg = await self.session_repo.add_message(asst_msg)
            session.touch()
            await self.session_repo.update(session)
            await self.uow.commit()

        return ChatTurnResult(
            message=created_asst_msg,
            tools_executed=tools_executed,
            is_secret=session.is_secret,
            privacy_trigger_detected=privacy_trigger_detected,
            suggest_secret_mode=suggest_secret_mode,
            is_turn_secret=is_turn_secret,
        )

    async def _open_turn(self, session_id: str, current_user: User):
        """
        The checks both streamed turns share, and the pair they need afterwards.

        Everything here is about whether this person may speak to this agent at all; nothing in it
        depends on there being a new question, which is why regenerating can run it unchanged.
        """
        session = await self.session_repo.get_by_id(session_id)
        if not session:
            raise EntityNotFoundException("Session not found")

        if session.user_id != current_user.id:
            raise ZeroLeakViolationException(
                "Zero-Leak Privacy violation: You cannot chat in another member's session."
            )

        if session.is_archived or session.agent_id is None:
            raise InvalidOperationException("Cannot send messages to an archived conversation session.")

        agent = await self.agent_repo.get_by_id(session.agent_id)
        if not agent:
            raise EntityNotFoundException("Agent personality not found")

        if agent.deleted_at is not None:
            raise InvalidOperationException(
                "Cannot send messages while the agent is in trash. Restore the agent to continue chatting."
            )

        if not agent.is_active:
            raise InvalidOperationException(
                f"Agent '{agent.name}' is deactivated and cannot accept new messages."
            )

        return session, agent

    async def regenerate_stream(
        self, session_id: str, current_user: User, timezone_name: Optional[str] = None
    ):
        """
        Answer the last question again, without asking it again.

        A turn can arrive, run, and fail to produce an answer — the model times out, the upstream
        gateway gives up. The question is stored and perfectly good; only the answer is missing.
        Re-sending the question would store it twice and make the transcript stutter, so this
        picks up from the messages already there.

        The privacy decision is read back from the question rather than recomputed. It was made
        when the question was asked, and a turn that answered it differently the second time would
        be a turn that leaked.
        """
        session, agent = await self._open_turn(session_id, current_user)

        recent_messages = await self.session_repo.get_messages_after(session.id, session.summarized_through_id)
        last_message = recent_messages[-1] if recent_messages else None
        if last_message is None or last_message.role != "user":
            raise InvalidOperationException(
                "There is no unanswered question in this conversation to answer again."
            )

        privacy_trigger_detected = bool(
            (last_message.metadata_json or {}).get("privacy_trigger_detected", False)
        )

        # The question was written down long ago, so there is nothing to wait for before saying so.
        yield {"type": "accepted"}

        async for event in self._answer_or_say_it_failed(
            session=session,
            agent=agent,
            current_user=current_user,
            recent_messages=recent_messages,
            privacy_trigger_detected=privacy_trigger_detected,
            timezone_name=timezone_name,
        ):
            yield event

    async def execute_stream(
        self,
        session_id: str,
        current_user: User,
        content: str,
        timezone_name: Optional[str] = None,
    ):
        session, agent = await self._open_turn(session_id, current_user)

        privacy_trigger_detected = self._check_privacy_triggers(content)

        async with self.uow:
            user_msg = ChatMessage(
                session_id=session.id,
                role="user",
                content=content,
                metadata_json={"privacy_trigger_detected": privacy_trigger_detected},
            )
            await self.session_repo.add_message(user_msg)
            session.touch()
            await self.session_repo.update(session)
            await self.uow.commit()

        # The earliest honest thing to tell the phone. From here on the question is on the hub, so
        # whatever breaks is the answer's problem, never the question's - and the phone stops
        # offering to send it again.
        yield {"type": "accepted"}

        recent_messages = await self.session_repo.get_messages_after(session.id, session.summarized_through_id)

        async for event in self._answer_or_say_it_failed(
            session=session,
            agent=agent,
            current_user=current_user,
            recent_messages=recent_messages,
            privacy_trigger_detected=privacy_trigger_detected,
            timezone_name=timezone_name,
        ):
            yield event

    async def _answer_or_say_it_failed(self, **kwargs):
        """
        [_stream_answer], and the one ending it cannot reach on its own.

        By the time this runs the question is on the hub — both callers write it down first, and
        regenerating reads one that was written down long ago. So a model that dies here has lost
        the answer and nothing else, and that is a different sentence on screen from a question
        that never arrived: this one offers a fresh answer, the other offers to send it again.
        Telling someone the wrong one is what sends them into asking twice.

        A turn refused before any of that — an archived conversation, an agent in the trash, a
        session that is not yours — raises out of `_open_turn` instead and never reaches here.
        """
        async for event in self._say_if_it_fails(kwargs["session"], self._stream_answer(**kwargs)):
            yield event

    @staticmethod
    async def _say_if_it_fails(session, events):
        """[events], ended by `turn_failed` rather than an exception if the answer dies part way."""
        try:
            async for event in events:
                yield event
        except Exception as exc:
            logger.error(
                "Answer failed for session %s: %s", session.id, exc, exc_info=True
            )
            yield {"type": "turn_failed", "error": str(exc)}

    @staticmethod
    async def _relay(
        stream,
        final_content_parts: List[str],
        answer: _AnswerParts,
        tool_calls: Optional[List[LLMToolCall]] = None,
        finish_reasons: Optional[List[str]] = None,
    ):
        """
        One model stream, turned into what the phone is sent.

        Reasoning goes out as it arrives and is never kept: it is worth watching and worth nothing
        afterwards, so it stays out of `final_content_parts` and out of the saved answer. Words go
        out and are kept. Tool calls are collected for the caller when it asks for them.

        [answer] records each in its place. A stretch of thinking ends at the first word, at a tool
        call - so the tool's own running time is not counted as thought - or with the stream.
        """
        async for chunk in stream:
            if chunk.delta_reasoning:
                answer.thinking()
                yield {"type": "reasoning", "content": chunk.delta_reasoning}
            if chunk.delta_content:
                answer.text(chunk.delta_content)
                final_content_parts.append(chunk.delta_content)
                yield {"type": "delta", "content": chunk.delta_content}
            if chunk.tool_calls:
                answer.stop_thinking()
                if tool_calls is not None:
                    tool_calls.extend(chunk.tool_calls)
            if chunk.finish_reason and finish_reasons is not None:
                finish_reasons.append(chunk.finish_reason)
        answer.stop_thinking()

    async def _run_tool(
        self,
        tc: LLMToolCall,
        agent,
        user_id: str,
        is_turn_secret: bool,
        sources: Optional[TurnSources],
        offered: List[Dict[str, Any]],
    ) -> ToolExecutionResult:
        """
        Runs one tool call. A name the agent can't use - misspelled, or garbled with tool-call markup,
        as a model has sent - comes back as a failed result naming the tools it does have. It used
        to raise past the loop and end the whole turn, and the model never learnt why.
        """
        try:
            result = await self.tool_executor.execute(
                tool_name=tc.name,
                arguments=tc.arguments,
                user_id=user_id,
                agent_tool_permissions=agent.tool_permissions,
                is_secret_mode=is_turn_secret,
                sources=sources,
            )
        except ToolPermissionDeniedException:
            shown = " ".join(tc.name.split())[:60]
            names = ", ".join(tool["function"]["name"] for tool in offered)
            result = ToolExecutionResult(
                tool_name=tc.name,
                success=False,
                error=f"There is no tool '{shown}'. Your tools are: {names}.",
            )
        _log_call(tc, result, is_turn_secret)
        return result

    @staticmethod
    def _answer_now(question: str) -> str:
        """
        The last thing the model reads before a forced answer. Quoting the question puts it at the
        end of the prompt, where the model reads best, however much research came after it.
        """
        instruction = (
            "Tool budget reached. Please synthesize the findings gathered so far and provide your "
            "final response to the user."
        )
        return f"{instruction}\n\nThe question was: {question}" if question else instruction

    async def decide_stream(
        self,
        session_id: str,
        current_user: User,
        tool_call_id: str,
        approved: bool,
        modified_arguments: Optional[Dict[str, Any]] = None,
        timezone_name: Optional[str] = None,
    ):
        """
        The member's answer to one card, and the rest of the turn once every card has one.

        The turn paused with its proposals saved on the answer (see `_finish`). While any card is
        still waiting this only records the decision and says the turn is still paused. After the
        last one the approved writes run, the declined ones are told to the model, and the same
        answer carries on from where it paused, streamed like any other turn.

        Nothing about the paused turn was kept in memory, so a model that was unloaded or a hub that
        restarted in between costs only a slower first word.
        """
        session, agent = await self._open_turn(session_id, current_user)

        recent_messages = await self.session_repo.get_messages_after(session.id, session.summarized_through_id)
        card_to_decide(recent_messages, tool_call_id)
        paused_answer = recent_messages[-1]

        metadata = dict(paused_answer.metadata_json)
        parts = [dict(part) for part in metadata.get("parts") or []]
        for part in parts:
            if part.get("type") == "proposal" and part.get("tool_call_id") == tool_call_id:
                part["status"] = "approved" if approved else "declined"
                if approved and modified_arguments:
                    # Kept with the card, so the model can be told once the last card is answered.
                    edited = member_changes(part["arguments"], modified_arguments)
                    part["arguments"] = {**part["arguments"], **modified_arguments}
                    if edited:
                        part["edited"] = edited

        yield {"type": "accepted"}

        if any(part.get("type") == "proposal" and part.get("status") == "pending" for part in parts):
            # Several writes in one step: the turn carries on after the last decision, not this one.
            async with self.uow:
                paused_answer.metadata_json = {**metadata, "parts": parts}
                await self.session_repo.update_message(paused_answer)
                await self.uow.commit()
            yield self._awaiting_event(
                paused_answer,
                agent,
                is_turn_secret=bool(metadata.get("privacy_trigger_detected")) or session.is_secret,
                suggest_secret_mode=bool(metadata.get("suggest_secret_mode")),
            )
            return

        async def carry_on():
            paused = metadata[PAUSED_TURN]
            turn = await self._begin(
                session=session,
                agent=agent,
                current_user=current_user,
                recent_messages=recent_messages[:-1],
                privacy_trigger_detected=bool(metadata.get("privacy_trigger_detected")),
                timezone_name=timezone_name,
                parts=parts,
                tools_executed=metadata.get("tools_executed"),
            )
            turn.llm_messages.extend(load_messages(paused.get("messages") or []))
            turn.first_round = int(paused.get("round") or 0) + 1
            turn.out_of_room = bool(paused.get("out_of_room"))
            turn.question = paused.get("question") or turn.question
            turn.message = paused_answer

            for index, part in enumerate(turn.answer.parts):
                if part.get("type") != "proposal":
                    continue
                call = LLMToolCall(id=part["tool_call_id"], name=part["tool"], arguments=part["arguments"])
                if part["status"] == "approved":
                    edited = part.get("edited")
                    if edited:
                        # The call the model reads back is the call that ran, not the one it proposed.
                        for message in turn.llm_messages:
                            for proposed in message.tool_calls or []:
                                if proposed.id == call.id:
                                    proposed.arguments = call.arguments
                    async for event in self._run_call(turn, call, current_user.id, at=index, edited=edited):
                        yield event
                else:
                    summary = describe_write(call.name, call.arguments)
                    turn.answer.parts[index] = {"type": "declined", "tool": call.name, "summary": summary}
                    yield {"type": "tool_declined", "tool": call.name, "summary": summary}
                    turn.llm_messages.append(
                        LLMMessage(
                            role="tool",
                            tool_call_id=call.id,
                            name=call.name,
                            content=json.dumps({"declined": DECLINED}),
                        )
                    )

            # The writes have happened. Saved before the model is asked for the rest, so an answer
            # that dies from here on can never run them a second time.
            await self._save(turn, paused=False)

            async for event in self._rounds(turn, current_user):
                yield event
            async for event in self._finish(turn):
                yield event

        async for event in self._say_if_it_fails(session, carry_on()):
            yield event

    async def _stream_answer(
        self,
        session,
        agent,
        current_user: User,
        recent_messages,
        privacy_trigger_detected: bool,
        timezone_name: Optional[str] = None,
    ):
        """Generate and persist the answer. Everything a turn does once the question is settled."""
        turn = await self._begin(
            session=session,
            agent=agent,
            current_user=current_user,
            recent_messages=recent_messages,
            privacy_trigger_detected=privacy_trigger_detected,
            timezone_name=timezone_name,
        )
        async for event in self._rounds(turn, current_user):
            yield event
        async for event in self._finish(turn):
            yield event

    async def _begin(
        self,
        session,
        agent,
        current_user: User,
        recent_messages,
        privacy_trigger_detected: bool,
        timezone_name: Optional[str],
        parts: Optional[List[Dict[str, Any]]] = None,
        tools_executed: Optional[List[Dict[str, Any]]] = None,
    ) -> "_Turn":
        """A turn ready for its first round: the model, its context, the tools it may use."""
        model = await self.model_resolver.for_agent(agent)
        is_turn_secret = privacy_trigger_detected or session.is_secret

        llm_messages = await self.context_assembler.execute(
            user=current_user,
            agent=agent,
            recent_messages=recent_messages,
            is_secret_session=session.is_secret,
            is_turn_secret=is_turn_secret,
            timezone_name=timezone_name,
            history_summary=session.history_summary,
        )

        tool_defs_res = self.tool_lister.execute()
        available_tools_defs = (
            await tool_defs_res if hasattr(tool_defs_res, "__await__") else tool_defs_res
        )
        agent_tools = []
        if agent.tool_permissions:
            granted = effective_tool_permissions(agent.tool_permissions)
            for td in available_tools_defs:
                if td.name in granted:
                    agent_tools.append(
                        {
                            "type": "function",
                            "function": {
                                "name": td.name,
                                "description": td.description,
                                "parameters": td.parameters_schema,
                            },
                        }
                    )

        return _Turn(
            session=session,
            agent=agent,
            model=model,
            llm_messages=llm_messages,
            turn_starts_at=len(llm_messages),
            agent_tools=agent_tools,
            answer=_AnswerParts(self.clock, parts),
            tools_executed=list(tools_executed or []),
            question=next((m.content for m in reversed(recent_messages) if m.role == "user"), ""),
            privacy_trigger_detected=privacy_trigger_detected,
            # Read afresh each time a turn starts or carries on, so a box ticked on the card applies
            # to the rest of the same answer.
            auto_approved=await auto_approved(self.approval_repo, current_user.id),
            is_turn_secret=is_turn_secret,
            suggest_secret_mode=privacy_trigger_detected and not session.is_secret,
            sources=(
                TurnSources(self.source_index_factory.new())
                if agent_tools and self.source_index_factory is not None
                else None
            ),
            budget=_ContextBudget(self.context_window_tokens, self.answer_reserve_tokens),
        )

    async def _rounds(self, turn: "_Turn", current_user: User):
        """
        One model call per round, until a round calls no tool: that round is the answer (#35).

        The model may keep using tools until the last round of the budget, or until the window has
        no room for another round; then it is made to answer with none. A write that needs asking
        pauses the turn instead: the round's other calls run first, then each write becomes a card
        and the turn stops until the member decides (`decide_stream`).
        """
        for round_number in range(turn.first_round, self.max_iterations + 1):
            if not turn.agent_tools:
                tools = None
            elif (
                round_number == self.max_iterations
                or turn.out_of_room
                or not turn.budget.has_room_for_a_round(turn.llm_messages, turn.agent_tools)
            ):
                turn.llm_messages.append(LLMMessage(role="system", content=self._answer_now(turn.question)))
                tools = None
            else:
                tools = turn.agent_tools

            # Streamed rather than blocking. A blocking call sends nothing back until the whole
            # response exists, so a thinking model deciding on a tool used to hold the socket
            # silent until it timed out, and an agent with tools answered in one lump at the end.
            # Ollama sends a tool call whole, in one chunk, so it is simply collected.
            round_words: List[str] = []
            tool_calls: List[LLMToolCall] = []
            async for event in self._relay(
                self.llm_client.stream_chat_completion(
                    messages=turn.llm_messages,
                    model=turn.model,
                    temperature=turn.agent.temperature,
                    top_p=turn.agent.top_p,
                    tools=tools,
                ),
                round_words,
                turn.answer,
                tool_calls,
                turn.finish_reasons,
            ):
                yield event

            if tools is None or not tool_calls:
                return

            turn.llm_messages.append(
                LLMMessage(role="assistant", content="".join(round_words), tool_calls=tool_calls)
            )

            asks = [tc for tc in tool_calls if needs_asking(tc, turn.auto_approved, turn.is_turn_secret)]
            for tc in tool_calls:
                if not any(tc is ask for ask in asks):
                    # A write that runs without a card here does so because the member made it automatic.
                    auto = tc.name in WRITE_ACTIONS and not turn.is_turn_secret
                    async for event in self._run_call(turn, tc, current_user.id, auto=auto):
                        yield event

            if asks:
                for tc in asks:
                    part = proposal_part(tc)
                    turn.answer.parts.append(part)
                    yield proposal_event(part)
                turn.paused_in_round = round_number
                return

    async def _run_call(
        self,
        turn: "_Turn",
        tc: LLMToolCall,
        user_id: str,
        at: Optional[int] = None,
        auto: bool = False,
        edited: Optional[Dict[str, Any]] = None,
    ):
        """
        Runs one call and records it: a step in the answer, and a result for the model to read.

        [edited] are the details the member changed on the card before approving. The model is told
        beside the result, or it would go on to describe the write as it proposed it.
        """
        yield {"type": "tool_executing", "tool": tc.name, "arguments": tc.arguments}
        tool_result = await self._run_tool(
            tc, turn.agent, user_id, turn.is_turn_secret, sources=turn.sources, offered=turn.agent_tools
        )
        summary = summarize_tool(tc.name, tc.arguments, tool_result)
        exec_info = {
            "tool": tc.name,
            "success": tool_result.success,
            "arguments": tc.arguments,
            "data": tool_result.data,
            "error": tool_result.error,
            "summary": summary,
        }
        if auto:
            exec_info["auto"] = True
        turn.tools_executed.append(exec_info)
        turn.answer.tool(tc.name, tool_result.success, summary, at=at, auto=auto)
        yield {"type": "tool_result", "data": exec_info}
        # The phone is sent the whole result; the model reads what fits.
        result = tool_result.data if tool_result.success else {"error": tool_result.error}
        seen, no_room = turn.budget.fit(
            told_with_edit(result, edited) if edited else result,
            turn.llm_messages,
            turn.agent_tools,
        )
        turn.out_of_room = turn.out_of_room or no_room
        turn.llm_messages.append(LLMMessage(role="tool", tool_call_id=tc.id, name=tc.name, content=seen))

    async def _finish(self, turn: "_Turn"):
        """Saves the answer, and says it is done or waiting on the member."""
        paused = turn.paused_in_round is not None
        cut_off = False
        if not paused:
            # The model stopped because the window was full, not because it was done. Saved as such,
            # so it is never mistaken for a finished answer.
            cut_off = bool(turn.finish_reasons) and turn.finish_reasons[-1] == "length"
            if cut_off:
                logger.warning("Answer in session %s was cut off by the context window", turn.session.id)

        saved = await self._save(turn, paused=paused, cut_off=cut_off)

        if paused:
            yield self._awaiting_event(saved, turn.agent, turn.is_turn_secret, turn.suggest_secret_mode)
            return

        yield {
            "cut_off": cut_off,
            "type": "done",
            "message_id": saved.id,
            "assistant_content": saved.content,
            "suggest_secret_mode": turn.suggest_secret_mode,
            "is_turn_secret": turn.is_turn_secret,
            "agent_id": turn.agent.id,
            "agent_name": turn.agent.name,
            "tools_executed": turn.tools_executed,
            "parts": turn.answer.parts,
        }

    async def _save(self, turn: "_Turn", paused: bool, cut_off: bool = False) -> ChatMessage:
        """
        Writes the answer as it stands: a new message the first time, the same one after a pause.

        A paused answer also keeps what the turn needs to carry on (PAUSED_TURN): the model's side
        of the turn so far and the round it paused in.
        """
        metadata: Dict[str, Any] = {
            "tools_executed": turn.tools_executed,
            "parts": turn.answer.parts,
            "privacy_trigger_detected": turn.privacy_trigger_detected,
            "suggest_secret_mode": turn.suggest_secret_mode,
        }
        if cut_off:
            metadata["cut_off"] = True
        if paused:
            metadata[PAUSED_TURN] = {
                "messages": dump_messages(turn.llm_messages[turn.turn_starts_at:]),
                "round": turn.paused_in_round,
                "out_of_room": turn.out_of_room,
                "question": turn.question,
            }

        async with self.uow:
            if turn.message is None:
                turn.message = await self.session_repo.add_message(
                    ChatMessage(
                        session_id=turn.session.id,
                        role="assistant",
                        content=turn.answer.content(),
                        metadata_json=metadata,
                    )
                )
            else:
                turn.message.content = turn.answer.content()
                turn.message.metadata_json = metadata
                turn.message = await self.session_repo.update_message(turn.message)
            turn.session.touch()
            await self.session_repo.update(turn.session)
            await self.uow.commit()
        return turn.message

    @staticmethod
    def _awaiting_event(message: ChatMessage, agent, is_turn_secret: bool, suggest_secret_mode: bool) -> Dict[str, Any]:
        """The end of a turn that is waiting on the member's cards, not finished."""
        return {
            "type": "awaiting_approval",
            "message_id": message.id,
            "assistant_content": message.content,
            "suggest_secret_mode": suggest_secret_mode,
            "is_turn_secret": is_turn_secret,
            "agent_id": agent.id,
            "agent_name": agent.name,
            "parts": (message.metadata_json or {}).get("parts") or [],
        }


@dataclass
class _Turn:
    """What a streamed turn carries from round to round, and across a pause for approval."""

    session: ConversationSession
    agent: Any
    model: str
    llm_messages: List[LLMMessage]
    # Where this turn's own messages start: everything before is the assembled context, rebuilt
    # rather than saved when a paused turn carries on.
    turn_starts_at: int
    agent_tools: List[Dict[str, Any]]
    answer: _AnswerParts
    tools_executed: List[Dict[str, Any]]
    question: str
    privacy_trigger_detected: bool
    # The (tool, action) writes the member lets agents do without asking.
    auto_approved: FrozenSet[Tuple[str, str]]
    is_turn_secret: bool
    suggest_secret_mode: bool
    sources: Optional[TurnSources]
    budget: _ContextBudget
    first_round: int = 1
    out_of_room: bool = False
    finish_reasons: List[str] = field(default_factory=list)
    paused_in_round: Optional[int] = None
    # The saved answer this turn carries on, once there is one.
    message: Optional[ChatMessage] = None
