import logging
from typing import Callable, List, Optional
from datetime import datetime, timezone

from app.domain.entities.user import User
from app.domain.entities.agent import AgentPersonality
from app.domain.entities.session import ChatMessage
from app.domain.entities.llm_message import LLMMessage
from app.domain.repositories.memory_repository import IMemoryRepository
from app.domain.use_cases.memories.memory_block import BuildMemoryBlockUseCase, MemoryBlock
from app.domain.use_cases.memories.recall_memories import RECALLED
from app.domain.repositories.agent_repository import IAgentRepository
from app.domain.repositories.user_repository import IUserRepository
from app.domain.use_cases.chat.current_date_line import sent_at_stamp, today_line
from app.domain.use_cases.chat.token_estimate import estimate_tokens

logger = logging.getLogger(__name__)


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


def _with_recalled(message: ChatMessage) -> str:
    content = message.content or ""
    recalled = [item for item in (message.metadata_json or {}).get(RECALLED) or [] if isinstance(item, dict)]
    if message.role != "user" or not recalled:
        return content
    lines = "\n".join(f"- {item.get('text', '')}" for item in recalled)
    return f"{content}\n\n[Memories that may help]:\n{lines}"


class AssembleAgentContextUseCase:
    def __init__(
        self,
        memory_repo: IMemoryRepository,
        user_repo: Optional[IUserRepository] = None,
        agent_repo: Optional[IAgentRepository] = None,
        history_tokens: int = 6000,
        clock: Callable[[], datetime] = _utc_now,
        memory_block_tokens: int = 1000,
    ):
        self.memory_repo = memory_repo
        self.user_repo = user_repo
        self.agent_repo = agent_repo
        # How much of the window the conversation's own messages may take, word for word.
        self.history_tokens = history_tokens
        # Tells the agent what day it is. Injected so a test can fix it.
        self.clock = clock
        # The fixed block of memories, filled to a token budget (memory spec §5.1).
        self.memory_block = BuildMemoryBlockUseCase(
            memory_repo, user_repo=user_repo, agent_repo=agent_repo, budget_tokens=memory_block_tokens,
            clock=lambda: self.clock(),
        )

    async def execute(
        self,
        user: User,
        agent: AgentPersonality,
        recent_messages: List[ChatMessage],
        is_secret_session: bool = False,
        is_turn_secret: bool = False,
        timezone_name: Optional[str] = None,
        history_summary: Optional[str] = None,
    ) -> List[LLMMessage]:
        is_secret = is_secret_session or is_turn_secret

        block = await self.memory_block.execute(user.id, is_secret=is_secret)
        logger.info(
            "Memory block | member=%s tokens=%d/%d in=%d left_out=%d left_out_ids=%s",
            user.id, block.tokens_used, block.budget_tokens, len(block.included), len(block.left_out),
            ",".join(m.id for m in block.left_out),
        )

        # 4. Construct System Prompt with Attention-Optimized Boundary Ordering
        # --- PRIMACY ZONE (Top) ---
        primacy_lines = []
        if agent.system_prompt:
            primacy_lines.append(agent.system_prompt.strip())
        primacy_lines.append(
            f"You are speaking with {user.full_name}. Maintain helpful, attentive, and relational awareness."
        )
        # Last in the primacy zone: read early, and the recency zone's order is left alone (#39).
        # The date only: the time of day comes with each message, so this line changes once a day
        # and Ollama keeps everything cached after it (#79).
        primacy_lines.append(today_line(self.clock(), timezone_name))
        if is_secret:
            primacy_lines.append(
                "[CONFIDENTIALITY NOTICE - SECRET MODE ACTIVE]: This conversation is strictly private. "
                "Do NOT share anything discussed with the household. External write tools are restricted."
            )

        # --- RECENCY ZONE (Bottom of system prompt, right above conversation turns) ---
        recency_lines = []

        recency_lines.extend(self._memory_lines(block))

        # Sandwich reminder for secret mode
        if is_secret:
            recency_lines.append(
                "[Reminder: Secret Mode is active. Keep all disclosures confidential.]"
            )

        full_system_text = "\n\n".join(primacy_lines + recency_lines)

        # 5. Assemble LLM Messages
        llm_messages: List[LLMMessage] = [
            LLMMessage(role="system", content=full_system_text)
        ]

        # 6. The conversation: what came before in the chat's own summary, then the newest messages
        # word for word, as many as fit [history_tokens], with the question always last and whole.
        # Older messages used to be sent as their first 100 characters, which kept nothing useful;
        # the summary is written after turns by SummarizeHistoryUseCase, and a message it hasn't
        # caught up with yet is left out whole for a turn rather than cut short.
        if history_summary:
            llm_messages.append(
                LLMMessage(role="system", content=f"[Earlier in this conversation]:\n{history_summary}")
            )
        # Each of the member's messages carries when it was sent, from its stored time, so it reads
        # the same on every later turn and the newest one tells the model the time now (#79).
        # What recall attached to a message comes after its words, exactly as it was attached, so the
        # prompt reads the same on every later turn (memory spec §5.2).
        for msg in self._newest_that_fit(recent_messages):
            content = _with_recalled(msg)
            if msg.role == "user":
                content = f"[Sent {sent_at_stamp(msg.created_at, timezone_name)}] {content}"
            llm_messages.append(LLMMessage(role=msg.role, content=content))

        return llm_messages

    @staticmethod
    def _memory_lines(block: MemoryBlock) -> List[str]:
        """The block's memories under their two headings, a household line saying who said it."""
        lines: List[str] = []
        personal = [m for m in block.included if m.scope != "household"]
        household = [m for m in block.included if m.scope == "household"]
        if personal:
            lines.append("[What I Know About You]:")
            lines.extend(f"- {block.lines[m.id]}" for m in personal)
        if household:
            lines.append("[Household Shared Knowledge - Do Not Re-Extract]:")
            lines.extend(f"- {block.lines[m.id]}" for m in household)
        return lines

    def _newest_that_fit(self, messages: List[ChatMessage]) -> List[ChatMessage]:
        kept: List[ChatMessage] = []
        used = 0.0
        for msg in reversed(messages):
            cost = estimate_tokens(_with_recalled(msg))
            if kept and used + cost > self.history_tokens:
                break
            kept.append(msg)
            used += cost
        return list(reversed(kept))
