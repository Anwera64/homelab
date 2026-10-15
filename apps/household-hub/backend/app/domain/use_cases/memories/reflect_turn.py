from dataclasses import dataclass, field
from datetime import datetime, timezone
import json
import logging
import re
from typing import Callable, List, Optional

from app.domain.entities.llm_message import LLMMessage
from app.domain.entities.memory import AgentMemory
from app.domain.repositories.llm_client import ILLMClient
from app.domain.repositories.memory_repository import IMemoryRepository
from app.domain.repositories.session_repository import ISessionRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.use_cases.chat.current_date_line import current_date_line
from app.domain.use_cases.memories.embed_memories import EmbedMemoriesUseCase
from app.domain.use_cases.models.resolve_agent_model import ResolveAgentModelUseCase

logger = logging.getLogger(__name__)

EXTRACTION_SYSTEM_PROMPT = """You are an autonomous memory reflection engine for a household AI assistant.
Your task is to analyze the conversation turn and extract long-term memories.

Return a valid JSON object ONLY with the following schema:
{
  "title": "Short descriptive title for this conversation (only if requested/first turn)",
  "memories": [
    {
      "category": "preference" | "fact" | "milestone" | "health" | "project",
      "content": "concise statement of the fact/preference",
      "scope": "personal" | "household",
      "confidence": 0.0 to 1.0,
      "expires_at": "ISO-8601 timestamp string, or null if it stays true",
      "relevant_for": ["up to 5 short situations where this matters, e.g. \"planning dinner\""]
    }
  ]
}

Guidelines:
- Only extract persistent facts, preferences, or significant milestones. Do NOT extract ephemeral chatter.
- Use "household" scope for what the rest of the household should know: events, schedules, deadlines, shared plans.
- relevant_for lists the kinds of questions or plans this memory would help with, not the memory reworded.
- Give expires_at to a memory that stops mattering on a date, such as an event or a deadline.
- High confidence (>=0.7) should only be assigned to clear, factual, or explicit user statements.
- Never output markdown formatting or commentary outside the JSON object.
"""


# Kept apart from EXTRACTION_SYSTEM_PROMPT so that prompt stays the same on every call.
DATE_RESOLUTION_GUIDANCE = (
    "Write dates as absolute dates: turn relative ones such as \"tomorrow\" or \"on Friday\" into the "
    "calendar date they mean, and count expires_at from today."
)


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


def _parse_relevant_for(value) -> Optional[List[str]]:
    """The model's `relevant_for` list, without blanks or non-text items; None when there is nothing usable."""
    if not isinstance(value, list):
        return None
    items = [item.strip() for item in value if isinstance(item, str) and item.strip()]
    return items[:5] or None


def _parse_expiry(value) -> Optional[datetime]:
    """An ISO-8601 expiry as the model wrote it; anything unreadable means the memory doesn't expire."""
    if not value:
        return None
    try:
        parsed = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    except ValueError:
        return None
    return parsed if parsed.tzinfo else parsed.replace(tzinfo=timezone.utc)


@dataclass
class ReflectTurnResult:
    session_title: Optional[str] = None
    memories_created: List[AgentMemory] = field(default_factory=list)
    memories_updated: List[AgentMemory] = field(default_factory=list)



class ReflectTurnUseCase:
    def __init__(
        self,
        llm_client: ILLMClient,
        memory_repo: IMemoryRepository,
        session_repo: ISessionRepository,
        uow: IUnitOfWork,
        model_resolver: ResolveAgentModelUseCase,
        confidence_threshold: float = 0.70,
        clock: Callable[[], datetime] = _utc_now,
        embed_memories: Optional[EmbedMemoriesUseCase] = None,
    ):
        self.llm_client = llm_client
        # Gives each new memory its vectors, so recall can find it (memory spec §4.3).
        self.embed_memories = embed_memories
        self.memory_repo = memory_repo
        self.session_repo = session_repo
        self.uow = uow
        self.confidence_threshold = confidence_threshold
        self.model_resolver = model_resolver
        # Tells the model what day it is, so "Friday" and expiry dates land on real days. Injected
        # so a test can fix it.
        self.clock = clock

    async def execute(
        self,
        session_id: str,
        user_id: str,
        username: str,
        agent_id: str,
        agent_name: str,
        user_message: str,
        assistant_message: str,
        is_secret_session: bool = False,
        is_turn_secret: bool = False,
        is_first_turn: bool = False,
        timezone_name: Optional[str] = None,
    ) -> ReflectTurnResult:
        prompt = (
            f"User '{username}': {user_message}\n"
            f"Assistant '{agent_name}': {assistant_message}\n\n"
            f"First turn: {is_first_turn}. Please extract title and memories in JSON format."
        )

        messages = [
            LLMMessage(role="system", content=EXTRACTION_SYSTEM_PROMPT),
            LLMMessage(
                role="system",
                content=f"{current_date_line(self.clock(), timezone_name)} {DATE_RESOLUTION_GUIDANCE}",
            ),
            LLMMessage(role="user", content=prompt),
        ]

        response = await self.llm_client.chat_completion(
            messages=messages,
            model=await self.model_resolver.default(),
            temperature=0.1,
        )

        content = response.content or ""
        parsed = self._parse_json(content)

        memories_to_create: List[AgentMemory] = []
        memories_to_update: List[AgentMemory] = []
        updated_title: Optional[str] = None

        is_secret = is_secret_session or is_turn_secret

        # 1. Update session title if first turn
        if is_first_turn and "title" in parsed and parsed["title"]:
            updated_title = str(parsed["title"]).strip()
            session = await self.session_repo.get_by_id(session_id)
            if session:
                session.title = updated_title
                await self.session_repo.update(session)

        # 2. Extract memories with deduplication
        raw_memories = parsed.get("memories", [])
        if isinstance(raw_memories, list):
            existing_memories = await self.memory_repo.list_user_memories(
                user_id=user_id, active_only=True
            )
            for item in raw_memories:
                if not isinstance(item, dict):
                    continue
                try:
                    conf = float(item.get("confidence", 0.0))
                except (ValueError, TypeError):
                    conf = 0.0

                if conf < self.confidence_threshold:
                    continue

                mem_content = str(item.get("content", "")).strip()
                if not mem_content:
                    continue

                scope = str(item.get("scope", "personal")).lower()
                if is_secret or scope not in ("personal", "household"):
                    scope = "personal"

                category = str(item.get("category", "fact")).lower()

                # Check if identical memory already exists for this user and scope
                normalized_content = mem_content.lower()
                duplicate = next(
                    (
                        m for m in existing_memories
                        if m.scope == scope and m.content.strip().lower() == normalized_content
                    ),
                    None,
                )

                if duplicate:
                    # Update confidence and timestamp instead of inserting duplicate row
                    duplicate.confidence = max(duplicate.confidence, conf)
                    duplicate.updated_at = datetime.now(timezone.utc)
                    updated_mem = await self.memory_repo.update(duplicate)
                    memories_to_update.append(updated_mem)
                else:
                    mem = AgentMemory(
                        user_id=user_id,
                        agent_id=agent_id,
                        scope=scope,
                        category=category,
                        content=mem_content,
                        confidence=conf,
                        source_session_id=session_id,
                        expires_at=_parse_expiry(item.get("expires_at")),
                        origin="reflection",
                        relevant_for=_parse_relevant_for(item.get("relevant_for")),
                    )
                    created_mem = await self.memory_repo.create(mem)
                    memories_to_create.append(created_mem)
                    existing_memories.append(created_mem)

        async with self.uow:
            await self.uow.commit()

        if self.embed_memories:
            await self.embed_memories.execute(memories_to_create)

        return ReflectTurnResult(
            session_title=updated_title,
            memories_created=memories_to_create,
            memories_updated=memories_to_update,
        )


    def _parse_json(self, raw: str) -> dict:
        text = raw.strip()
        if "```json" in text:
            match = re.search(r"```json\s*(.*?)\s*```", text, re.DOTALL)
            if match:
                text = match.group(1).strip()
        elif "```" in text:
            match = re.search(r"```\s*(.*?)\s*```", text, re.DOTALL)
            if match:
                text = match.group(1).strip()

        try:
            res = json.loads(text)
            if isinstance(res, dict):
                return res
        except Exception:
            start = text.find("{")
            end = text.rfind("}")
            if start != -1 and end != -1 and end > start:
                try:
                    res = json.loads(text[start : end + 1])
                    if isinstance(res, dict):
                        return res
                except Exception:
                    pass
        return {}
