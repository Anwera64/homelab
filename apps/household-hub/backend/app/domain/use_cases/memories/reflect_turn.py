from dataclasses import dataclass, field
from datetime import datetime, timezone
import json
import logging
import re
from typing import List, Optional

from app.domain.entities.gossip_milestone import GossipMilestone
from app.domain.entities.llm_message import LLMMessage
from app.domain.entities.memory import AgentMemory
from app.domain.repositories.gossip_repository import IGossipRepository
from app.domain.repositories.llm_client import ILLMClient
from app.domain.repositories.memory_repository import IMemoryRepository
from app.domain.repositories.session_repository import ISessionRepository
from app.domain.repositories.unit_of_work import IUnitOfWork

logger = logging.getLogger(__name__)

EXTRACTION_SYSTEM_PROMPT = """You are an autonomous memory reflection engine for a household AI assistant.
Your task is to analyze the conversation turn and extract long-term memories and broadcastable household milestones.

Return a valid JSON object ONLY with the following schema:
{
  "title": "Short descriptive title for this conversation (only if requested/first turn)",
  "memories": [
    {
      "category": "preference" | "fact" | "milestone" | "health" | "project",
      "content": "concise statement of the fact/preference",
      "scope": "personal" | "household",
      "confidence": 0.0 to 1.0
    }
  ],
  "milestones": [
    {
      "category": "milestone" | "schedule_constraint" | "academic_deadline" | "dietary_preference" | "project_update",
      "summary": "Brief broadcastable summary of important household event, schedule, or deadline",
      "expires_at": "ISO-8601 timestamp string or null"
    }
  ]
}

Guidelines:
- Only extract persistent facts, preferences, or significant milestones. Do NOT extract ephemeral chatter.
- High confidence (>=0.7) should only be assigned to clear, factual, or explicit user statements.
- Never output markdown formatting or commentary outside the JSON object.
"""


@dataclass
class ReflectTurnResult:
    session_title: Optional[str] = None
    memories_created: List[AgentMemory] = field(default_factory=list)
    milestones_created: List[GossipMilestone] = field(default_factory=list)


class ReflectTurnUseCase:
    def __init__(
        self,
        llm_client: ILLMClient,
        memory_repo: IMemoryRepository,
        gossip_repo: IGossipRepository,
        session_repo: ISessionRepository,
        uow: IUnitOfWork,
        confidence_threshold: float = 0.70,
        model: str = "qwen3:14b",
    ):
        self.llm_client = llm_client
        self.memory_repo = memory_repo
        self.gossip_repo = gossip_repo
        self.session_repo = session_repo
        self.uow = uow
        self.confidence_threshold = confidence_threshold
        self.model = model

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
    ) -> ReflectTurnResult:
        prompt = (
            f"User '{username}': {user_message}\n"
            f"Assistant '{agent_name}': {assistant_message}\n\n"
            f"First turn: {is_first_turn}. Please extract title, memories, and milestones in JSON format."
        )

        messages = [
            LLMMessage(role="system", content=EXTRACTION_SYSTEM_PROMPT),
            LLMMessage(role="user", content=prompt),
        ]

        response = await self.llm_client.chat_completion(
            messages=messages,
            model=self.model,
            temperature=0.1,
        )

        content = response.content or ""
        parsed = self._parse_json(content)

        memories_to_create: List[AgentMemory] = []
        milestones_to_create: List[GossipMilestone] = []
        updated_title: Optional[str] = None

        is_secret = is_secret_session or is_turn_secret

        # 1. Update session title if first turn
        if is_first_turn and "title" in parsed and parsed["title"]:
            updated_title = str(parsed["title"]).strip()
            session = await self.session_repo.get_by_id(session_id)
            if session:
                session.title = updated_title
                await self.session_repo.update(session)

        # 2. Extract memories
        raw_memories = parsed.get("memories", [])
        if isinstance(raw_memories, list):
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

                mem = AgentMemory(
                    user_id=user_id,
                    agent_id=agent_id,
                    scope=scope,
                    category=category,
                    content=mem_content,
                    confidence=conf,
                    source_session_id=session_id,
                )
                created_mem = await self.memory_repo.create(mem)
                memories_to_create.append(created_mem)

        # 3. Extract milestones (strictly dropped if secret)
        if not is_secret:
            raw_milestones = parsed.get("milestones", [])
            if isinstance(raw_milestones, list):
                for item in raw_milestones:
                    if not isinstance(item, dict):
                        continue
                    summary = str(item.get("summary", "")).strip()
                    if not summary:
                        continue

                    category = str(item.get("category", "milestone")).lower()
                    expires_at_val = item.get("expires_at")
                    exp_dt = None
                    if expires_at_val:
                        try:
                            exp_str = str(expires_at_val).replace("Z", "+00:00")
                            exp_dt = datetime.fromisoformat(exp_str)
                        except Exception:
                            exp_dt = None

                    milestone = GossipMilestone(
                        source_user_id=user_id,
                        source_username=username,
                        reporting_agent_id=agent_id,
                        reporting_agent_name=agent_name,
                        category=category,
                        summary=summary,
                        expires_at=exp_dt,
                        source_session_id=session_id,
                    )
                    published = await self.gossip_repo.publish(milestone)
                    milestones_to_create.append(published)

        async with self.uow:
            await self.uow.commit()

        return ReflectTurnResult(
            session_title=updated_title,
            memories_created=memories_to_create,
            milestones_created=milestones_to_create,
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
