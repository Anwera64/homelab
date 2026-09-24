import json
import logging
import re
import time
from dataclasses import dataclass, field
from typing import Any, Callable, Dict, List, Optional

from app.domain.entities.user import User
from app.domain.entities.session import ChatMessage, ConversationSession
from app.domain.entities.llm_message import LLMMessage, LLMToolCall
from app.domain.repositories.session_repository import ISessionRepository
from app.domain.repositories.agent_repository import IAgentRepository
from app.domain.repositories.llm_client import ILLMClient
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.use_cases.chat.assemble_agent_context import AssembleAgentContextUseCase
from app.domain.use_cases.integrations.execute_tool import ExecuteToolUseCase
from app.domain.use_cases.integrations.list_available_tools import ListAvailableToolsUseCase
from app.domain.use_cases.models.resolve_agent_model import ResolveAgentModelUseCase
from app.domain.exceptions import (
    EntityNotFoundException,
    ZeroLeakViolationException,
    InvalidOperationException,
)


logger = logging.getLogger(__name__)


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

    def __init__(self, clock: Callable[[], float]) -> None:
        self._clock = clock
        self._thinking_since: Optional[float] = None
        self.parts: List[Dict[str, Any]] = []

    def thinking(self) -> None:
        if self._thinking_since is None:
            self._thinking_since = self._clock()

    def text(self, content: str) -> None:
        self.stop_thinking()
        if self.parts and self.parts[-1]["type"] == "text":
            self.parts[-1]["content"] += content
        else:
            self.parts.append({"type": "text", "content": content})

    def tool(self, name: str, success: bool) -> None:
        self.stop_thinking()
        self.parts.append({"type": "tool", "tool": name, "success": success})

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
            if part["type"] == "tool":
                tool_since_text = True
            elif part["type"] == "text":
                if written and tool_since_text:
                    written = written.rstrip() + "\n\n" + part["content"].lstrip()
                else:
                    written += part["content"]
                tool_since_text = False
        return written


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
        auto_approve_writes: bool = False,
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
        recent_messages = await self.session_repo.get_messages(session.id, limit=30)
        llm_messages = await self.context_assembler.execute(
            user=current_user,
            agent=agent,
            recent_messages=recent_messages,
            is_secret_session=session.is_secret,
            is_turn_secret=is_turn_secret,
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
                if tc.name in {"calendar_write", "document_writer"} and not auto_approve_writes:
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
                    tool_result = await self.tool_executor.execute(
                        tool_name=tc.name,
                        arguments=tc.arguments,
                        user_id=current_user.id,
                        agent_tool_permissions=agent.tool_permissions,
                        is_secret_mode=is_turn_secret,
                        role="assistant",
                    )
                    exec_info = {
                        "tool": tc.name,
                        "success": tool_result.success,
                        "arguments": tc.arguments,
                        "data": tool_result.data,
                        "error": tool_result.error,
                    }
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

    async def regenerate_stream(self, session_id: str, current_user: User):
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

        recent_messages = await self.session_repo.get_messages(session.id, limit=30)
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
            auto_approve_writes=False,
        ):
            yield event

    async def execute_stream(
        self,
        session_id: str,
        current_user: User,
        content: str,
        auto_approve_writes: bool = False,
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

        recent_messages = await self.session_repo.get_messages(session.id, limit=30)

        async for event in self._answer_or_say_it_failed(
            session=session,
            agent=agent,
            current_user=current_user,
            recent_messages=recent_messages,
            privacy_trigger_detected=privacy_trigger_detected,
            auto_approve_writes=auto_approve_writes,
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
        session = kwargs["session"]
        try:
            async for event in self._stream_answer(**kwargs):
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
        answer.stop_thinking()

    async def _stream_answer(
        self,
        session,
        agent,
        current_user: User,
        recent_messages,
        privacy_trigger_detected: bool,
        auto_approve_writes: bool,
    ):
        """Generate and persist the answer. Everything a turn does once the question is settled."""
        model = await self.model_resolver.for_agent(agent)
        is_turn_secret = privacy_trigger_detected or session.is_secret
        suggest_secret_mode = privacy_trigger_detected and not session.is_secret

        llm_messages = await self.context_assembler.execute(
            user=current_user,
            agent=agent,
            recent_messages=recent_messages,
            is_secret_session=session.is_secret,
            is_turn_secret=is_turn_secret,
        )

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

        tools_executed: List[Dict[str, Any]] = []
        final_content_parts: List[str] = []
        answer = _AnswerParts(self.clock)
        iterations = 0

        if not agent_tools:
            async for event in self._relay(
                self.llm_client.stream_chat_completion(
                    messages=llm_messages,
                    model=model,
                    temperature=agent.temperature,
                    top_p=agent.top_p,
                    tools=None,
                ),
                final_content_parts,
                answer,
            ):
                yield event
        else:
            while iterations < self.max_iterations:
                iterations += 1

                if iterations == self.max_iterations:
                    llm_messages.append(
                        LLMMessage(
                            role="system",
                            content="Tool budget reached. Please synthesize the findings gathered so far and provide your final response to the user.",
                        )
                    )
                    async for event in self._relay(
                        self.llm_client.stream_chat_completion(
                            messages=llm_messages,
                            model=model,
                            temperature=agent.temperature,
                            top_p=agent.top_p,
                            tools=None,
                        ),
                        final_content_parts,
                        answer,
                    ):
                        yield event
                    break

                # Streamed rather than blocking. A blocking call sends nothing back until the whole
                # response exists, so a thinking model deciding on a tool used to hold the socket
                # silent until it timed out, and an agent with tools answered in one lump at the end.
                # Ollama sends a tool call whole, in one chunk, so it is simply collected.
                decision_starts_at = len(final_content_parts)
                tool_calls: List[LLMToolCall] = []
                async for event in self._relay(
                    self.llm_client.stream_chat_completion(
                        messages=llm_messages,
                        model=model,
                        temperature=agent.temperature,
                        top_p=agent.top_p,
                        tools=agent_tools,
                    ),
                    final_content_parts,
                    answer,
                    tool_calls,
                ):
                    yield event

                if not tool_calls:
                    break

                llm_messages.append(
                    LLMMessage(
                        role="assistant",
                        content="".join(final_content_parts[decision_starts_at:]),
                        tool_calls=tool_calls,
                    )
                )

                for tc in tool_calls:
                    if tc.name in {"calendar_write", "document_writer"} and not auto_approve_writes:
                        proposal_info = {
                            "tool": tc.name,
                            "status": "proposal_pending",
                            "arguments": tc.arguments,
                            "message": f"Action '{tc.name}' requires member confirmation.",
                        }
                        tools_executed.append(proposal_info)
                        yield {"type": "tool_call", "data": proposal_info}
                        llm_messages.append(
                            LLMMessage(
                                role="tool",
                                tool_call_id=tc.id,
                                name=tc.name,
                                content=json.dumps(proposal_info),
                            )
                        )
                    else:
                        yield {"type": "tool_executing", "tool": tc.name, "arguments": tc.arguments}
                        tool_result = await self.tool_executor.execute(
                            tool_name=tc.name,
                            arguments=tc.arguments,
                            user_id=current_user.id,
                            agent_tool_permissions=agent.tool_permissions,
                            is_secret_mode=is_turn_secret,
                            role="assistant",
                        )
                        exec_info = {
                            "tool": tc.name,
                            "success": tool_result.success,
                            "arguments": tc.arguments,
                            "data": tool_result.data,
                            "error": tool_result.error,
                        }
                        tools_executed.append(exec_info)
                        answer.tool(tc.name, tool_result.success)
                        yield {"type": "tool_result", "data": exec_info}
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

                # After executing tools, stream final synthesis to the user
                async for event in self._relay(
                    self.llm_client.stream_chat_completion(
                        messages=llm_messages,
                        model=model,
                        temperature=agent.temperature,
                        top_p=agent.top_p,
                        tools=None,
                    ),
                    final_content_parts,
                    answer,
                ):
                    yield event
                break

        final_content = answer.content()

        async with self.uow:
            asst_msg = ChatMessage(
                session_id=session.id,
                role="assistant",
                content=final_content,
                metadata_json={
                    "tools_executed": tools_executed,
                    "parts": answer.parts,
                    "privacy_trigger_detected": privacy_trigger_detected,
                    "suggest_secret_mode": suggest_secret_mode,
                },
            )
            created_asst_msg = await self.session_repo.add_message(asst_msg)
            session.touch()
            await self.session_repo.update(session)
            await self.uow.commit()

        yield {
            "type": "done",
            "message_id": created_asst_msg.id,
            "assistant_content": final_content,
            "suggest_secret_mode": suggest_secret_mode,
            "is_turn_secret": is_turn_secret,
            "agent_id": agent.id,
            "agent_name": agent.name,
            "tools_executed": tools_executed,
            "parts": answer.parts,
        }

