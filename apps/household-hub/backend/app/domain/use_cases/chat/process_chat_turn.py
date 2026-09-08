import json
import re
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional

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
from app.domain.exceptions import (
    EntityNotFoundException,
    ZeroLeakViolationException,
    InvalidOperationException,
)


@dataclass
class ChatTurnResult:
    message: ChatMessage
    tools_executed: List[Dict[str, Any]] = field(default_factory=list)
    is_secret: bool = False
    privacy_trigger_detected: bool = False
    suggest_secret_mode: bool = False
    is_turn_secret: bool = False


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
        max_iterations: int = 5,
    ):
        self.session_repo = session_repo
        self.agent_repo = agent_repo
        self.llm_client = llm_client
        self.context_assembler = context_assembler
        self.tool_executor = tool_executor
        self.tool_lister = tool_lister
        self.uow = uow
        self.max_iterations = max_iterations

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
                    model=agent.model_alias or "qwen3:14b",
                    temperature=agent.temperature,
                    top_p=agent.top_p,
                    tools=None,
                )
                final_content = final_resp.content
                break

            resp = await self.llm_client.chat_completion(
                messages=llm_messages,
                model=agent.model_alias or "qwen3:14b",
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

    async def execute_stream(
        self,
        session_id: str,
        current_user: User,
        content: str,
        auto_approve_writes: bool = False,
    ):
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

        privacy_trigger_detected = self._check_privacy_triggers(content)
        is_turn_secret = privacy_trigger_detected or session.is_secret
        suggest_secret_mode = privacy_trigger_detected and not session.is_secret

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

        recent_messages = await self.session_repo.get_messages(session.id, limit=30)
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
        iterations = 0

        if not agent_tools:
            async for chunk in self.llm_client.stream_chat_completion(
                messages=llm_messages,
                model=agent.model_alias or "qwen3:14b",
                temperature=agent.temperature,
                top_p=agent.top_p,
                tools=None,
            ):
                if chunk.delta_content:
                    final_content_parts.append(chunk.delta_content)
                    yield {"type": "delta", "content": chunk.delta_content}
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
                    async for chunk in self.llm_client.stream_chat_completion(
                        messages=llm_messages,
                        model=agent.model_alias or "qwen3:14b",
                        temperature=agent.temperature,
                        top_p=agent.top_p,
                        tools=None,
                    ):
                        if chunk.delta_content:
                            final_content_parts.append(chunk.delta_content)
                            yield {"type": "delta", "content": chunk.delta_content}
                    break

                resp = await self.llm_client.chat_completion(
                    messages=llm_messages,
                    model=agent.model_alias or "qwen3:14b",
                    temperature=agent.temperature,
                    top_p=agent.top_p,
                    tools=agent_tools if agent_tools else None,
                )

                if not resp.tool_calls:
                    if resp.content:
                        final_content_parts.append(resp.content)
                        yield {"type": "delta", "content": resp.content}
                    break

                llm_messages.append(
                    LLMMessage(role="assistant", content=resp.content, tool_calls=resp.tool_calls)
                )

                for tc in resp.tool_calls:
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
                async for chunk in self.llm_client.stream_chat_completion(
                    messages=llm_messages,
                    model=agent.model_alias or "qwen3:14b",
                    temperature=agent.temperature,
                    top_p=agent.top_p,
                    tools=None,
                ):
                    if chunk.delta_content:
                        final_content_parts.append(chunk.delta_content)
                        yield {"type": "delta", "content": chunk.delta_content}
                break

        final_content = "".join(final_content_parts)

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

        yield {
            "type": "done",
            "message_id": created_asst_msg.id,
            "assistant_content": final_content,
            "suggest_secret_mode": suggest_secret_mode,
            "is_turn_secret": is_turn_secret,
            "agent_id": agent.id,
            "agent_name": agent.name,
            "tools_executed": tools_executed,
        }

