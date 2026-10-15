import re
from typing import Callable, List, Optional
from datetime import datetime, timezone

from app.domain.entities.user import User
from app.domain.entities.agent import AgentPersonality
from app.domain.entities.session import ChatMessage
from app.domain.entities.llm_message import LLMMessage
from app.domain.repositories.memory_repository import IMemoryRepository
from app.domain.entities.memory import AgentMemory
from app.domain.repositories.agent_repository import IAgentRepository
from app.domain.repositories.user_repository import IUserRepository
from app.domain.use_cases.chat.current_date_line import sent_at_stamp, today_line
from app.domain.use_cases.chat.token_estimate import estimate_tokens


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


def _sanitize_prompt_snippet(text: str, max_length: int = 500) -> str:
    if not text:
        return ""
    patterns = [
        r"(?i)\bsystem\s*:",
        r"(?i)\bhuman\s*:",
        r"(?i)\bassistant\s*:",
        r"(?i)<\|.*?\|>",
        r"###",
        r"---",
    ]
    cleaned = text
    for pattern in patterns:
        cleaned = re.sub(pattern, " ", cleaned)
    cleaned = re.sub(r"\s+", " ", cleaned).strip()
    if len(cleaned) > max_length:
        cleaned = cleaned[:max_length]
    return cleaned


def _unexpired(memories: List[AgentMemory], now: datetime) -> List[AgentMemory]:
    """An expired memory is left out of the prompt (memory spec §3.1)."""

    def expired(memory: AgentMemory) -> bool:
        if memory.expires_at is None:
            return False
        # SQLite gives datetimes back without their zone; every one the hub writes is UTC.
        expires_at = memory.expires_at if memory.expires_at.tzinfo else memory.expires_at.replace(tzinfo=timezone.utc)
        return expires_at <= now

    return [m for m in memories if not expired(m)]


class AssembleAgentContextUseCase:
    def __init__(
        self,
        memory_repo: IMemoryRepository,
        user_repo: Optional[IUserRepository] = None,
        agent_repo: Optional[IAgentRepository] = None,
        history_tokens: int = 6000,
        clock: Callable[[], datetime] = _utc_now,
    ):
        self.memory_repo = memory_repo
        self.user_repo = user_repo
        self.agent_repo = agent_repo
        # How much of the window the conversation's own messages may take, word for word.
        self.history_tokens = history_tokens
        # Tells the agent what day it is. Injected so a test can fix it.
        self.clock = clock

    async def _household_lines(self, memories: List[AgentMemory]) -> List[str]:
        """
        One line per household memory, saying who said it and to which agent (memory spec §3.2).
        A member who has left is named in the past, so agents stop planning around them while what
        they shared keeps their name on it. Names are looked up once per assembly, not per line.
        """
        members = {member.id: member for member in await self.user_repo.list_all()} if self.user_repo else {}
        agent_names: dict = {}
        for agent_id in {m.agent_id for m in memories if m.agent_id}:
            agent = await self.agent_repo.get_by_id(agent_id) if self.agent_repo else None
            agent_names[agent_id] = agent.name if agent else None

        lines = []
        for memory in memories:
            member = members.get(memory.user_id)
            source = _sanitize_prompt_snippet(member.full_name, max_length=50) if member else ""
            source = source or "A member"
            if member and not member.is_active:
                source = f"{source}, who's no longer in the household,"
            agent_name = _sanitize_prompt_snippet(agent_names.get(memory.agent_id) or "", max_length=50)
            told = f"the {agent_name}" if agent_name else "an agent"
            lines.append(f"- {source} said to {told}: {_sanitize_prompt_snippet(memory.content)}")
        return lines

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
        now = self.clock()

        # 1. Fetch user personal memories
        personal_memories = _unexpired(
            await self.memory_repo.list_user_memories(user_id=user.id, scope="personal", active_only=True), now
        )

        # 2. Fetch household memories (if not in secret mode). They are the household bus.
        household_memories = []
        if not is_secret:
            household_memories = _unexpired(await self.memory_repo.list_household_memories(), now)

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

        # Personal memories
        if personal_memories:
            recency_lines.append("[What I Know About You]:")
            for m in personal_memories[:20]:
                created_str = m.created_at.strftime("%b %Y") if hasattr(m, "created_at") and m.created_at else "Recent"
                safe_content = _sanitize_prompt_snippet(m.content)
                recency_lines.append(f"- [Added {created_str}] {safe_content} (confidence: {m.confidence:.2f})")

        # Household memories, with who said them and an anti-echo tag. 25 is the 10 household
        # memories and 15 milestones this replaces; the fixed block's token budget comes in its own PR.
        if household_memories:
            recency_lines.append("[Household Shared Knowledge - Do Not Re-Extract]:")
            recency_lines.extend(await self._household_lines(household_memories[:25]))

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
        for msg in self._newest_that_fit(recent_messages):
            content = msg.content or ""
            if msg.role == "user":
                content = f"[Sent {sent_at_stamp(msg.created_at, timezone_name)}] {content}"
            llm_messages.append(LLMMessage(role=msg.role, content=content))

        return llm_messages

    def _newest_that_fit(self, messages: List[ChatMessage]) -> List[ChatMessage]:
        kept: List[ChatMessage] = []
        used = 0.0
        for msg in reversed(messages):
            cost = estimate_tokens(msg.content)
            if kept and used + cost > self.history_tokens:
                break
            kept.append(msg)
            used += cost
        return list(reversed(kept))
