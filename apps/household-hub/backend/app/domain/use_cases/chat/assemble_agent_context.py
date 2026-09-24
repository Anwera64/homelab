import re
from typing import List, Optional
from datetime import datetime, timezone

from app.domain.entities.user import User
from app.domain.entities.agent import AgentPersonality
from app.domain.entities.session import ChatMessage
from app.domain.entities.llm_message import LLMMessage
from app.domain.repositories.memory_repository import IMemoryRepository
from app.domain.repositories.gossip_repository import IGossipRepository
from app.domain.repositories.user_repository import IUserRepository
from app.domain.use_cases.chat.token_estimate import estimate_tokens


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


class AssembleAgentContextUseCase:
    def __init__(
        self,
        memory_repo: IMemoryRepository,
        gossip_repo: IGossipRepository,
        user_repo: Optional[IUserRepository] = None,
        history_tokens: int = 6000,
    ):
        self.memory_repo = memory_repo
        self.gossip_repo = gossip_repo
        self.user_repo = user_repo
        # How much of the window the conversation's own messages may take, word for word.
        self.history_tokens = history_tokens

    async def _active_member_ids(self) -> Optional[set]:
        """Who is still here, or None when nobody asked us to tell the difference."""
        if not self.user_repo:
            return None
        return {member.id for member in await self.user_repo.list_active()}

    async def execute(
        self,
        user: User,
        agent: AgentPersonality,
        recent_messages: List[ChatMessage],
        is_secret_session: bool = False,
        is_turn_secret: bool = False,
        history_summary: Optional[str] = None,
    ) -> List[LLMMessage]:
        is_secret = is_secret_session or is_turn_secret

        # 1. Fetch user personal memories
        personal_memories = await self.memory_repo.list_user_memories(
            user_id=user.id, scope="personal", active_only=True
        )

        # 2. Fetch household memories (if not in secret mode)
        household_memories = []
        if not is_secret:
            household_memories = await self.memory_repo.list_household_memories()

        # 3. Fetch active non-expired gossip milestones
        household_milestones = []
        if not is_secret:
            household_milestones = await self.gossip_repo.get_active_household_milestones(limit=15)

        # 4. Construct System Prompt with Attention-Optimized Boundary Ordering
        # --- PRIMACY ZONE (Top) ---
        primacy_lines = []
        if agent.system_prompt:
            primacy_lines.append(agent.system_prompt.strip())
        primacy_lines.append(
            f"You are speaking with {user.full_name}. Maintain helpful, attentive, and relational awareness."
        )
        if is_secret:
            primacy_lines.append(
                "[CONFIDENTIALITY NOTICE - SECRET MODE ACTIVE]: This conversation is strictly private. "
                "Do NOT share anything discussed to the household or gossip bus. External write tools are restricted."
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

        # Household memories
        if household_memories:
            recency_lines.append("[Household Shared Knowledge]:")
            for hm in household_memories[:10]:
                safe_content = _sanitize_prompt_snippet(hm.content)
                recency_lines.append(f"- {safe_content}")

        # Household milestones with provenance attribution and anti-echo tags
        if household_milestones:
            # A member who has left is named in the past, so agents stop planning around them while
            # what they shared keeps their name on it.
            who_is_still_here = await self._active_member_ids()
            recency_lines.append("[Injected Household Context - Do Not Re-Extract]:")
            for gm in household_milestones:
                reporting = _sanitize_prompt_snippet(gm.reporting_agent_name, max_length=50) or "an agent"
                source = _sanitize_prompt_snippet(gm.source_username, max_length=50) or "A member"
                if who_is_still_here is not None and gm.source_user_id not in who_is_still_here:
                    source = f"{source}, who's no longer in the household,"
                safe_summary = _sanitize_prompt_snippet(gm.summary)
                recency_lines.append(f"- {source} mentioned to the {reporting}: {safe_summary}")

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
        for msg in self._newest_that_fit(recent_messages):
            llm_messages.append(LLMMessage(role=msg.role, content=msg.content or ""))

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
