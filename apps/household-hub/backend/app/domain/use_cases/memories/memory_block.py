import re
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Callable, Dict, List, Optional

from app.domain.entities.memory import AgentMemory
from app.domain.repositories.agent_repository import IAgentRepository
from app.domain.repositories.memory_repository import IMemoryRepository
from app.domain.repositories.user_repository import IUserRepository
from app.domain.use_cases.chat.token_estimate import estimate_tokens
from app.domain.use_cases.memories.event_sentence import memory_text

# Recall can't find these: "answer briefly" resembles no message (memory spec §5.1).
ALWAYS_FIRST = ("preference", "health")


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


def sanitize_prompt_snippet(text: str, max_length: int = 500) -> str:
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


def _aware(value: datetime) -> datetime:
    # SQLite gives datetimes back without their zone; every one the hub writes is UTC.
    return value if value.tzinfo else value.replace(tzinfo=timezone.utc)


def _unexpired(memories: List[AgentMemory], now: datetime) -> List[AgentMemory]:
    """An expired memory is left out of the prompt and of recall (memory spec §3.1)."""
    return [m for m in memories if m.expires_at is None or _aware(m.expires_at) > now]


@dataclass
class MemoryBlock:
    """What the fixed block holds, in the order it was filled, and the memories that didn't fit."""

    included: List[AgentMemory]
    left_out: List[AgentMemory]
    # The line each visible memory is shown as, in the block or when recalled.
    lines: Dict[str, str] = field(default_factory=dict)
    tokens_used: float = 0.0
    budget_tokens: int = 0

    @property
    def visible(self) -> List[AgentMemory]:
        return self.included + self.left_out


class BuildMemoryBlockUseCase:
    """
    The fixed block (memory spec §5.1). The member's own personal memories and the household's,
    never another member's personal ones; personal only in a secret chat. Preferences and health
    first, then everything else newest first, until the next line would pass the budget.
    """

    def __init__(
        self,
        memory_repo: IMemoryRepository,
        user_repo: Optional[IUserRepository] = None,
        agent_repo: Optional[IAgentRepository] = None,
        budget_tokens: int = 1000,
        clock: Callable[[], datetime] = _utc_now,
    ):
        self.memory_repo = memory_repo
        self.user_repo = user_repo
        self.agent_repo = agent_repo
        self.budget_tokens = budget_tokens
        self.clock = clock

    async def execute(self, user_id: str, is_secret: bool, timezone_name: Optional[str] = None) -> MemoryBlock:
        """[timezone_name] is the member's, as the phone sent it: an event memory's time is shown in it."""
        now = self.clock()
        memories = await self.memory_repo.list_user_memories(user_id=user_id, scope="personal", active_only=True)
        if not is_secret:
            memories = memories + await self.memory_repo.list_household_memories()
        memories = _unexpired([m for m in memories if m.is_active], now)

        newest_first = sorted(memories, key=lambda m: _aware(m.created_at), reverse=True)
        ordered = [m for m in newest_first if m.category in ALWAYS_FIRST] + [
            m for m in newest_first if m.category not in ALWAYS_FIRST
        ]
        lines = await self._lines(ordered, timezone_name)

        included: List[AgentMemory] = []
        used = 0.0
        for index, memory in enumerate(ordered):
            cost = estimate_tokens(lines[memory.id])
            if used + cost > self.budget_tokens:
                return MemoryBlock(included, ordered[index:], lines, used, self.budget_tokens)
            included.append(memory)
            used += cost
        return MemoryBlock(included, [], lines, used, self.budget_tokens)

    async def _lines(self, memories: List[AgentMemory], timezone_name: Optional[str] = None) -> Dict[str, str]:
        """
        A personal memory with when it was added; a household one with who said it and to which
        agent (memory spec §3.2). A member who has left is named in the past, so agents stop
        planning around them while what they shared keeps their name on it. The confidence figure
        is never printed (§5.1).
        """
        household = [m for m in memories if m.scope == "household"]
        members = {}
        if household and self.user_repo:
            members = {member.id: member for member in await self.user_repo.list_all()}
        agent_names: dict = {}
        for agent_id in {m.agent_id for m in household if m.agent_id}:
            agent = await self.agent_repo.get_by_id(agent_id) if self.agent_repo else None
            agent_names[agent_id] = agent.name if agent else None

        lines: Dict[str, str] = {}
        for memory in memories:
            content = sanitize_prompt_snippet(memory_text(memory, timezone_name))
            if memory.scope != "household":
                added = memory.created_at.strftime("%b %Y") if memory.created_at else "Recent"
                lines[memory.id] = f"[Added {added}] {content}"
                continue
            member = members.get(memory.user_id)
            source = sanitize_prompt_snippet(member.full_name, max_length=50) if member else ""
            source = source or "A member"
            if member and not member.is_active:
                source = f"{source}, who's no longer in the household,"
            agent_name = sanitize_prompt_snippet(agent_names.get(memory.agent_id) or "", max_length=50)
            told = f"the {agent_name}" if agent_name else "an agent"
            lines[memory.id] = f"{source} said to {told}: {content}"
        return lines
