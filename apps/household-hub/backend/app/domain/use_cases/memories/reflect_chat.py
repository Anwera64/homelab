import json
import logging
import re
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any, Callable, Dict, List, Optional, Tuple

from app.domain.entities.calendar_event import CalendarEvent
from app.domain.entities.llm_message import LLMMessage
from app.domain.entities.memory import AgentMemory
from app.domain.entities.memory_proposal import (
    DIFFERENT,
    FROM_REFLECTION,
    FROM_REMEMBER,
    PROPOSING,
    RELATIONS,
    SAME,
    UNCLEAR,
    MemoryProposal,
)
from app.domain.entities.session import ChatMessage, ConversationSession
from app.domain.repositories.embedder import IEmbedder
from app.domain.repositories.llm_client import ILLMClient
from app.domain.repositories.memory_proposal_repository import IMemoryProposalRepository
from app.domain.repositories.memory_repository import IMemoryRepository
from app.domain.repositories.memory_vector_repository import IMemoryVectorRepository
from app.domain.repositories.session_repository import ISessionRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.repositories.user_repository import IUserRepository
from app.domain.use_cases.chat.current_date_line import current_date_line, sent_at_stamp
from app.domain.use_cases.memories.embed_memories import EmbedMemoriesUseCase
from app.domain.use_cases.memories.event_links import (
    RemovedEvent,
    event_calendars,
    event_expiry,
    removed_events,
    touched_event_ids,
)
from app.domain.use_cases.memories.memory_block import BuildMemoryBlockUseCase, _aware, sanitize_prompt_snippet

logger = logging.getLogger(__name__)

CATEGORIES = ("preference", "fact", "milestone", "health", "project")
SCOPES = ("personal", "household")
# The agent's words are context only; a long answer is cut so it can't crowd out the member's.
AGENT_LINE_CHARACTERS = 1000
LOGGED_TEXT_CHARACTERS = 500

REFLECTION_SYSTEM_PROMPT = """You look over a finished conversation between a household member and their AI agent, and decide what is worth remembering about the member and their household.

Only what the member states is a source. The agent's lines are context for understanding the member, never a source: do not remember anything only the agent said, claimed or did.

You are shown the stored memories closest to this conversation, each with an id such as m1. For every fact the member stated that is worth keeping, name its relation to the closest stored memory:
- "same": already stored, in other words or another language
- "different": not stored; a separate fact
- "more_detail": agrees with a stored memory and adds something
- "changed": a stored memory is out of date
- "contradicts": the fact and a stored memory cannot both be true
- "unclear": you cannot tell
For every relation except "different", give "about": the id of that stored memory.

Return one JSON object only, with no markdown and nothing outside it:
{
  "title": "a short title for the conversation, only when asked for one",
  "facts": [
    {
      "content": "one concise statement, naming people rather than saying he or she",
      "category": "preference" | "fact" | "milestone" | "health" | "project",
      "scope": "personal" | "household",
      "expires_at": "ISO-8601 timestamp, or null if it stays true",
      "relevant_for": ["up to 5 short situations where this matters, e.g. \\"planning dinner\\""],
      "relation": "same" | "different" | "more_detail" | "changed" | "contradicts" | "unclear",
      "about": "m1",
      "event": "e1"
    }
  ]
}

Guidelines:
- Keep persistent facts, preferences, plans and significant events. Skip greetings, chatter and one-off requests.
- "household" is for what the rest of the household should know: events, schedules, deadlines, shared plans. Everything else is "personal".
- Write dates as absolute dates: turn "tomorrow" or "on Friday" into the date it meant when that line was sent, and give expires_at to a fact that stops mattering on a date.
- relevant_for lists the kinds of questions or plans the fact would help with, not the fact reworded.
- You may be shown calendar events the conversation created or changed, each with an id such as e1. A fact about one of them gives that id as "event"; leave "event" out otherwise. One fact per event is enough.
- You may also be shown calendar events that were removed, each with an id such as r1. They are no longer happening: save no fact about them. If a fact is about one anyway, give its id as "event".
- An empty "facts" list is a good answer when nothing is worth keeping.
"""

_THINK_RE = re.compile(r"<think>.*?</think>", re.DOTALL)


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


def _removed_when(event: RemovedEvent) -> str:
    """A removed event's start as the agent wrote it, already in the member's time: read as is, never converted."""
    try:
        start = datetime.fromisoformat(event.start).replace(tzinfo=None)
    except ValueError:
        return sanitize_prompt_snippet(event.start, max_length=40)
    stamp = sent_at_stamp(start, "UTC")
    return stamp.rsplit(",", 1)[0] if event.all_day else stamp


@dataclass
class ReflectionResult:
    through_id: Optional[str]
    added: List[str] = field(default_factory=list)
    proposed: List[str] = field(default_factory=list)
    same: int = 0
    title: Optional[str] = None
    model_called: bool = False


def _parse_json(raw: str) -> dict:
    text = _THINK_RE.sub("", raw or "").strip()
    fenced = re.search(r"```(?:json)?\s*(.*?)\s*```", text, re.DOTALL)
    if fenced:
        text = fenced.group(1).strip()
    for candidate in (text, text[text.find("{"): text.rfind("}") + 1] if "{" in text else ""):
        try:
            parsed = json.loads(candidate)
            if isinstance(parsed, dict):
                return parsed
        except (ValueError, TypeError):
            continue
    return {}


def _parse_relevant_for(value) -> Optional[List[str]]:
    if not isinstance(value, list):
        return None
    items = [item.strip() for item in value if isinstance(item, str) and item.strip()]
    return items[:5] or None


def _parse_expiry(value) -> Optional[datetime]:
    if not value:
        return None
    try:
        parsed = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    except ValueError:
        return None
    return parsed if parsed.tzinfo else parsed.replace(tzinfo=timezone.utc)


def _normalized(text: str) -> str:
    return " ".join((text or "").lower().split())


def without_private(messages: List[ChatMessage]) -> Tuple[List[ChatMessage], int]:
    """
    Leaves out each message that tripped a privacy phrase, and the agent's answer to it, which may
    repeat it (memory spec §2.2). Returns what is left and how many were left out.
    """
    kept: List[ChatMessage] = []
    skipping = False
    for message in messages:
        if message.role == "user":
            skipping = bool((message.metadata_json or {}).get("privacy_trigger_detected"))
        if not skipping:
            kept.append(message)
    return kept, len(messages) - len(kept)


class ReflectChatUseCase:
    """
    One chat's reflection, after it has gone quiet (memory spec §2.2). One model call over the
    messages after the chat's marker, on the chat agent's own model, with the stored memories
    closest to what the member said. Each fact the member stated is saved, dropped as already
    known, or raised as a proposal on the stored memory it would change (§6). Everything is written
    in one commit with the marker, so a run cancelled by a turn leaves nothing behind.
    """

    def __init__(
        self,
        llm_client: ILLMClient,
        session_repo: ISessionRepository,
        memory_repo: IMemoryRepository,
        proposal_repo: IMemoryProposalRepository,
        user_repo: IUserRepository,
        memory_block: BuildMemoryBlockUseCase,
        embedder: IEmbedder,
        vector_repo: IMemoryVectorRepository,
        embed_memories: EmbedMemoriesUseCase,
        uow: IUnitOfWork,
        embedding_model: str = "bge-m3",
        neighbour_bar: float = 0.45,
        neighbours_max: int = 15,
        clock: Callable[[], datetime] = _utc_now,
        log_text: bool = False,
        event_lookup: Optional[Any] = None,
    ):
        self.llm_client = llm_client
        self.session_repo = session_repo
        self.memory_repo = memory_repo
        self.proposal_repo = proposal_repo
        self.user_repo = user_repo
        self.memory_block = memory_block
        self.embedder = embedder
        self.vector_repo = vector_repo
        self.embed_memories = embed_memories
        self.uow = uow
        self.embedding_model = embedding_model
        self.neighbour_bar = neighbour_bar
        self.neighbours_max = neighbours_max
        self.clock = clock
        self.log_text = log_text
        # Reads an event the chat touched from the member's calendar (GetCalendarEventUseCase), so a
        # fact about it can be linked to it (memory spec §7). None: nothing is linked.
        self.event_lookup = event_lookup

    def _text(self, text: str) -> str:
        return f" text={text[:LOGGED_TEXT_CHARACTERS]!r}" if self.log_text else ""

    async def execute(self, session: ConversationSession, model: str, keep_alive: Optional[int]) -> ReflectionResult:
        started = time.monotonic()
        member_id, session_id = session.user_id, session.id
        messages = await self.session_repo.get_messages_after(session_id, session.reflected_through_id)
        if not messages:
            return ReflectionResult(through_id=session.reflected_through_id)
        through_id = messages[-1].id
        kept, private = without_private(messages)
        said = [m for m in kept if m.role == "user"]
        result = ReflectionResult(through_id=through_id)

        if not said:
            await self._commit(session, result, title=None)
            logger.info(
                "Reflection run | member=%s session=%s messages=%d private=%d outcome=nothing_from_member",
                member_id, session_id, len(messages), private,
            )
            return result

        zone = next(
            ((m.metadata_json or {}).get("timezone") for m in reversed(said) if (m.metadata_json or {}).get("timezone")),
            None,
        )
        visible = (await self.memory_block.execute(member_id, is_secret=False, timezone_name=zone))
        neighbours, how, scores = await self._neighbours(session, said, visible.visible)
        labels = {f"m{index}": memory for index, memory in enumerate(neighbours, start=1)}
        events, gone = await self._touched_events(session, kept)
        # Named so the model saves nothing about them; a fact tagged with one is dropped below.
        removed = {f"r{index}": event for index, event in enumerate(removed_events(kept, gone), start=1)}

        member = await self.user_repo.get_by_id(member_id)
        member_name = sanitize_prompt_snippet(member.full_name, max_length=50) if member else "The member"
        first_reflection = session.reflected_through_id is None
        prompt = self._prompt(
            labels, visible.lines, kept, member_name or "The member", zone, first_reflection, events, removed
        )

        response = await self.llm_client.chat_completion(
            messages=[
                LLMMessage(role="system", content=REFLECTION_SYSTEM_PROMPT),
                LLMMessage(role="user", content=prompt),
            ],
            model=model,
            temperature=0.1,
            keep_alive=keep_alive,
        )
        result.model_called = True
        parsed = _parse_json(response.content)

        title = None
        if first_reflection and isinstance(parsed.get("title"), str) and parsed["title"].strip():
            title = " ".join(parsed["title"].split())[:255]

        visible_text = {_normalized(m.content) for m in visible.visible}
        # Memories the member already asked, with `remember`, to change: their ask stands, so reading
        # the same change in the chat raises no second one (memory spec §2.1).
        asked = {
            p.memory_id for p in await self.proposal_repo.list_pending_from_user(member_id) if p.origin == FROM_REMEMBER
        }
        new_memories: List[AgentMemory] = []
        new_proposals: List[MemoryProposal] = []
        # Events already remembered, before this run or by an earlier fact of it: one memory each.
        linked = set()
        for event in events.values():
            if await self.memory_repo.list_by_event(member_id, event.id):
                linked.add(event.id)
        facts = parsed.get("facts") if isinstance(parsed.get("facts"), list) else []
        for fact in facts:
            if not isinstance(fact, dict):
                continue
            content = " ".join(str(fact.get("content") or "").split())
            if not content:
                continue
            relation = str(fact.get("relation") or "").lower().replace(" ", "_")
            if relation not in RELATIONS:
                relation = UNCLEAR
            about = labels.get(str(fact.get("about") or ""))
            event = events.get(str(fact.get("event") or ""))

            if str(fact.get("event") or "") in removed:
                outcome, target = "dropped_removed_event", None
                result.same += 1
            elif event is not None and event.id in linked:
                outcome, target = "dropped_already_linked", event.id
                result.same += 1
            elif _normalized(content) in visible_text:
                outcome, target = "dropped_same", None
                result.same += 1
            elif relation == SAME and about is not None:
                outcome, target = "dropped_same", about.id
                result.same += 1
            elif relation in PROPOSING and about is not None and about.id in asked:
                outcome, target = "skipped_already_asked", about.id
            elif relation in PROPOSING and about is not None:
                proposal = MemoryProposal(
                    memory_id=about.id,
                    content=content,
                    relation=relation,
                    answerer_id=about.user_id,
                    source_session_id=session_id,
                    source_user_id=member_id,
                    created_at=self.clock(),
                )
                new_proposals.append(proposal)
                outcome, target = "proposal", proposal.id
            else:
                if relation != DIFFERENT:
                    logger.info(
                        "Reflection fact | member=%s session=%s relation=%s about=%s unknown, saved as new",
                        member_id, session_id, relation, fact.get("about"),
                    )
                scope = str(fact.get("scope") or "personal").lower()
                category = str(fact.get("category") or "fact").lower()
                memory = AgentMemory(
                    user_id=member_id,
                    agent_id=session.agent_id,
                    scope=scope if scope in SCOPES else "personal",
                    category=category if category in CATEGORIES else "fact",
                    content=content,
                    source_session_id=session_id,
                    expires_at=_parse_expiry(fact.get("expires_at")),
                    origin="reflection",
                    relevant_for=_parse_relevant_for(fact.get("relevant_for")),
                )
                if event is not None:
                    memory.event_id = event.id
                    memory.event_calendar = event.calendar_key or None
                    memory.event_starts_at = event.start_time
                    memory.expires_at = event_expiry(event)
                    linked.add(event.id)
                new_memories.append(memory)
                visible_text.add(_normalized(content))
                outcome, target = "saved", memory.id
            logger.info(
                "Reflection fact | member=%s session=%s relation=%s about=%s score=%s outcome=%s id=%s event=%s%s",
                member_id, session_id, relation, about.id if about else None,
                f"{scores[about.id]:.3f}" if about and about.id in scores else None,
                outcome, target, event.id if event else None, self._text(content),
            )

        result.title = title
        await self._commit(session, result, title, new_memories, new_proposals)
        if new_memories:
            await self.embed_memories.execute(new_memories)

        usage = response.usage or {}
        logger.info(
            "Reflection run | member=%s session=%s messages=%d private=%d neighbours=%s offered=%s ms=%d "
            "prompt_tokens=%s answer_tokens=%s facts=%d added=%d proposed=%d same=%d",
            member_id, session_id, len(messages), private, how,
            ",".join(f"{m.id}={scores[m.id]:.3f}" if m.id in scores else m.id for m in neighbours),
            (time.monotonic() - started) * 1000, usage.get("prompt_tokens"), usage.get("completion_tokens"),
            len(facts), len(result.added), len(result.proposed), result.same,
        )
        if title:
            logger.info("Chat titled by reflection | member=%s session=%s%s", member_id, session_id, self._text(title))
        return result

    async def _neighbours(
        self, session: ConversationSession, said: List[ChatMessage], visible: List[AgentMemory]
    ) -> Tuple[List[AgentMemory], str, Dict[str, float]]:
        """The stored memories closest to what the member said (memory spec §6 step 1), with a low bar."""
        if not visible:
            return [], "none_stored", {}
        try:
            queries = await self.embedder.embed([m.content for m in said])
        except Exception as exc:
            logger.warning(
                "Reflection neighbours | member=%s session=%s embed failed, offering the newest memories: %s",
                session.user_id, session.id, exc,
            )
            newest = sorted(visible, key=lambda m: _aware(m.created_at), reverse=True)
            return newest[: self.neighbours_max], "newest", {}
        ids = [m.id for m in visible]
        best: Dict[str, float] = {}
        for query in queries:
            for memory_id, score in (await self.vector_repo.best_scores(query, ids, self.embedding_model)).items():
                best[memory_id] = max(score, best.get(memory_id, score))
        close = [m for m in visible if best.get(m.id, 0.0) >= self.neighbour_bar]
        close.sort(key=lambda m: best[m.id], reverse=True)
        return close[: self.neighbours_max], "similar", best

    async def _touched_events(
        self, session: ConversationSession, kept: List[ChatMessage]
    ) -> Tuple[Dict[str, CalendarEvent], List[str]]:
        """
        The events the chat created or changed that the calendar still has, labelled e1, e2… for the
        model, and the ids of those it no longer has. One the calendar can't be asked about is left
        out of both: a fact about it is then saved unlinked.
        """
        ids = touched_event_ids(kept)
        if not ids or self.event_lookup is None:
            return {}, []
        found: List[CalendarEvent] = []
        gone: List[str] = []
        calendars = event_calendars(kept)
        for event_id in ids:
            try:
                event = await self.event_lookup.execute(session.user_id, event_id, calendar=calendars.get(event_id))
            except Exception as exc:
                logger.warning(
                    "Reflection events | member=%s session=%s event=%s could not be read, not linked: %s",
                    session.user_id, session.id, event_id, exc,
                )
                continue
            if event is None:
                logger.info(
                    "Reflection events | member=%s session=%s event=%s gone from the calendar, named as removed",
                    session.user_id, session.id, event_id,
                )
                gone.append(event_id)
                continue
            found.append(event)
        return {f"e{index}": event for index, event in enumerate(found, start=1)}, gone

    def _prompt(
        self,
        labels: Dict[str, AgentMemory],
        lines: Dict[str, str],
        kept: List[ChatMessage],
        member_name: str,
        zone: Optional[str],
        first_reflection: bool,
        events: Optional[Dict[str, CalendarEvent]] = None,
        removed: Optional[Dict[str, RemovedEvent]] = None,
    ) -> str:
        parts = [current_date_line(self.clock(), zone), "", "Stored memories closest to this conversation:"]
        parts += [f"[{label}] {lines.get(m.id) or sanitize_prompt_snippet(m.content)}" for label, m in labels.items()]
        if not labels:
            parts.append("(none)")
        if events:
            parts += ["", "Calendar events this conversation created or changed:"]
            for label, event in events.items():
                when = (
                    sent_at_stamp(event.start_time, "UTC").rsplit(",", 1)[0]
                    if event.is_all_day
                    else sent_at_stamp(event.start_time, zone)
                )
                repeats = f" · repeats {event.repeat.frequency}" if event.repeat else ""
                parts.append(f"[{label}] {sanitize_prompt_snippet(event.title, max_length=120)} · {when}{repeats}")
        if removed:
            parts += ["", "Calendar events removed since; they are no longer happening:"]
            for label, gone in removed.items():
                parts.append(f"[{label}] {sanitize_prompt_snippet(gone.title, max_length=120)} · {_removed_when(gone)}")
        parts += ["", f"Conversation. Only {member_name}'s lines are a source:"]
        for message in kept:
            stamp = sent_at_stamp(message.created_at, zone)
            if message.role == "user":
                parts.append(f"{member_name} ({stamp}): {message.content}")
            elif message.role == "assistant" and message.content:
                parts.append(f"Agent ({stamp}, context only): {message.content[:AGENT_LINE_CHARACTERS]}")
        parts += [
            "",
            "Give the conversation a title." if first_reflection else "Leave the title out.",
            "Answer with the JSON object only.",
        ]
        return "\n".join(parts)

    async def _commit(
        self,
        session: ConversationSession,
        result: ReflectionResult,
        title: Optional[str],
        memories: List[AgentMemory] = (),
        proposals: List[MemoryProposal] = (),
    ) -> None:
        now = self.clock()
        async with self.uow:
            for memory in memories:
                await self.memory_repo.create(memory)
                result.added.append(memory.id)
            for proposal in proposals:
                # Only reflection's own older changes: one a member asked for is never dropped by it.
                dropped = await self.proposal_repo.drop_pending_for(proposal.memory_id, origin=FROM_REFLECTION)
                for dropped_id in dropped:
                    logger.info(
                        "Memory proposal dropped | memory=%s proposal=%s reason=newer_proposal",
                        proposal.memory_id, dropped_id,
                    )
                await self.proposal_repo.create(proposal)
                result.proposed.append(proposal.id)
                logger.info(
                    "Memory proposal created | member=%s session=%s memory=%s proposal=%s relation=%s answerer=%s",
                    session.user_id, session.id, proposal.memory_id, proposal.id, proposal.relation,
                    proposal.answerer_id,
                )
            if title:
                current = await self.session_repo.get_by_id(session.id)
                if current is not None:
                    current.title = title
                    await self.session_repo.update(current)
            outcome: Dict[str, Any] = {
                "added": list(result.added),
                "proposed": list(result.proposed),
                "retired": [],
                "at": now.isoformat(),
            }
            await self.session_repo.save_reflection(session.id, result.through_id, now, outcome)
            await self.uow.commit()
