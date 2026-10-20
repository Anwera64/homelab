import logging
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Callable, Dict, List, Optional, Tuple, Union

from app.domain.entities.memory import AgentMemory
from app.domain.entities.memory_proposal import CHANGED, FROM_REMEMBER, MemoryProposal
from app.domain.entities.user import User
from app.domain.exceptions import EntityNotFoundException, InvalidOperationException, ZeroLeakViolationException
from app.domain.repositories.agent_repository import IAgentRepository
from app.domain.repositories.memory_proposal_repository import IMemoryProposalRepository
from app.domain.repositories.memory_repository import IMemoryRepository
from app.domain.repositories.session_repository import ISessionRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.repositories.user_repository import IUserRepository
from app.domain.use_cases.memories.embed_memories import EmbedMemoriesUseCase
from app.domain.use_cases.memories.event_links import carry_event
from app.domain.use_cases.memories.list_memory_page import UNKNOWN_AGENT_NAME, MemoryItems
from app.domain.use_cases.memories.match_memory import HeldMatch, MatchMemoryUseCase, MemoryMatch
from app.domain.use_cases.memories.retire_memory import RetireMemoryUseCase
from app.domain.use_cases.memories.withdraw_change import WithdrawChangeUseCase

logger = logging.getLogger(__name__)

CATEGORIES = ("preference", "fact", "milestone", "health", "project")
SCOPES = ("personal", "household")
LOGGED_TEXT_CHARACTERS = 500

NOTHING_TO_FORGET = "No stored memory matches that. Nothing was forgotten."


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


def _expiry(value: Optional[str]) -> Optional[datetime]:
    if not value:
        return None
    try:
        parsed = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    except ValueError:
        return None
    return parsed if parsed.tzinfo else parsed.replace(tzinfo=timezone.utc)


async def speaker_name(user_repo: IUserRepository, user_id: str) -> str:
    user = await user_repo.get_by_id(user_id)
    return (user.full_name if user else "") or "Another member"


@dataclass
class Remembered:
    """What `remember` did: the memory it saved and the one it replaced, or, over another member's
    memory, the change that holds the sentence until they answer."""

    memory: Optional[AgentMemory]
    replaced: Optional[AgentMemory] = None
    kept_beside: Optional[AgentMemory] = None
    # Who said the memory it waits on, and will be asked which one is right.
    speaker_name: Optional[str] = None
    held: Optional[MemoryProposal] = None


class RememberUseCase:
    """
    `remember`, once the member approved it or made it automatic (memory spec §2.1).

    The new sentence is saved as the member's. When the call names a stored memory it changes
    ([replaces], or the [memory_id] its card showed), the member's own is retired in its favour.
    Another member's is never touched: the sentence waits in a change for them to check, and nothing
    is saved, so agents keep using theirs until they answer. Asked again while one waits on the same
    memory, the waiting one takes the new sentence.
    """

    def __init__(
        self,
        memory_repo: IMemoryRepository,
        proposal_repo: IMemoryProposalRepository,
        match: MatchMemoryUseCase,
        user_repo: IUserRepository,
        uow: IUnitOfWork,
        embed_memories: EmbedMemoriesUseCase,
        clock: Callable[[], datetime] = _utc_now,
        log_text: bool = False,
    ):
        self.memory_repo = memory_repo
        self.proposal_repo = proposal_repo
        self.match = match
        self.user_repo = user_repo
        self.uow = uow
        self.embed_memories = embed_memories
        self.clock = clock
        self.log_text = log_text

    async def execute(
        self,
        member: User,
        content: str,
        scope: Optional[str] = None,
        category: Optional[str] = None,
        replaces: Optional[str] = None,
        memory_id: Optional[str] = None,
        expires_at: Optional[str] = None,
        session_id: Optional[str] = None,
        agent_id: Optional[str] = None,
    ) -> Remembered:
        content = " ".join((content or "").split())
        if not content:
            raise InvalidOperationException("'content' is required: the sentence to remember.")
        found: Optional[MemoryMatch] = None
        if memory_id or (replaces and replaces.strip()):
            found = await self.match.execute(member.id, replaces or "", memory_id=memory_id or None)
        stored = found.memory if found else None
        score = f"{found.score:.3f}" if found and found.score is not None else None
        text = f" text={content[:LOGGED_TEXT_CHARACTERS]!r}" if self.log_text else ""

        if stored is not None and stored.user_id != member.id:
            done, outcome = await self._hold(member, content, stored, _expiry(expires_at), session_id)
            logger.info(
                "Memory remember | member=%s session=%s matched=%s score=%s outcome=%s new=%s proposal=%s%s",
                member.id, session_id or "none", stored.id, score, outcome, None, done.held.id, text,
            )
            return done

        scope = (scope or "personal").lower()
        category = (category or "fact").lower()
        new = AgentMemory(
            user_id=member.id,
            agent_id=agent_id,
            scope=scope if scope in SCOPES else "personal",
            category=category if category in CATEGORIES else "fact",
            content=content,
            source_session_id=session_id,
            expires_at=_expiry(expires_at),
            origin="tool",
            created_at=self.clock(),
            updated_at=self.clock(),
        )
        if stored is not None:
            # It takes the place of the stored one, so a later move or removal of its event still reaches it.
            carry_event(new, stored, link=True)
        done = Remembered(memory=new)
        async with self.uow:
            await self.memory_repo.create(new)
            if stored is not None:
                for dropped in await self.proposal_repo.drop_pending_for(stored.id):
                    logger.info(
                        "Memory proposal dropped | memory=%s proposal=%s reason=remembered", stored.id, dropped
                    )
                stored.is_active = False
                stored.replaced_by_id = new.id
                stored.retired_at = self.clock()
                await self.memory_repo.update(stored)
                done.replaced = stored
            await self.uow.commit()

        logger.info(
            "Memory remember | member=%s session=%s matched=%s score=%s outcome=%s new=%s proposal=%s%s",
            member.id, session_id or "none", stored.id if stored else None, score,
            "replaced" if done.replaced else "saved", new.id, None, text,
        )
        # Straight away, so recall can find it; the sweep fills it in later if the embedder is down.
        await self.embed_memories.execute([new])
        return done

    async def _hold(
        self,
        member: User,
        content: str,
        stored: AgentMemory,
        expires_at: Optional[datetime],
        session_id: Optional[str],
    ) -> Tuple[Remembered, str]:
        """The change that holds the sentence over another member's memory: the one already waiting
        from this member on that memory, given the new sentence, or a new one."""
        waiting = next(
            (
                p for p in await self.proposal_repo.list_pending_from_user(member.id)
                if p.memory_id == stored.id and p.origin == FROM_REMEMBER
            ),
            None,
        )
        async with self.uow:
            if waiting is not None:
                waiting.content = content
                waiting.expires_at = expires_at
                waiting.source_session_id = session_id
                waiting.created_at = self.clock()
                held = await self.proposal_repo.update(waiting)
            else:
                held = await self.proposal_repo.create(
                    MemoryProposal(
                        memory_id=stored.id,
                        content=content,
                        relation=CHANGED,
                        answerer_id=stored.user_id,
                        source_session_id=session_id,
                        source_user_id=member.id,
                        origin=FROM_REMEMBER,
                        expires_at=expires_at,
                        created_at=self.clock(),
                    )
                )
            await self.uow.commit()
        done = Remembered(
            memory=None,
            kept_beside=stored,
            speaker_name=await speaker_name(self.user_repo, stored.user_id),
            held=held,
        )
        return done, "held_replaced" if waiting is not None else "held"


class ForgetUseCase:
    """
    `forget`, once the member said yes on its card: it always asks (memory spec §2.1). Only the
    member's own memories can be forgotten, as with ✕ on the memory page; another's is refused by
    name, so the agent can say who can remove it. A sentence the member asked to remember that still
    waits on someone else is found too, by meaning, and forgetting it takes it back.
    """

    def __init__(
        self,
        match: MatchMemoryUseCase,
        retire: RetireMemoryUseCase,
        user_repo: IUserRepository,
        proposal_repo: Optional[IMemoryProposalRepository] = None,
        withdraw: Optional[WithdrawChangeUseCase] = None,
    ):
        self.match = match
        self.retire = retire
        self.user_repo = user_repo
        self.proposal_repo = proposal_repo
        self.withdraw = withdraw

    async def find(
        self, member: User, text: str, memory_id: Optional[str] = None, proposal_id: Optional[str] = None
    ) -> Union[MemoryMatch, HeldMatch]:
        """The member's own memory, or own waiting sentence, the call means; refused, with why, when there is none.
        [memory_id] or [proposal_id] is what a card already showed."""
        held = await self._held(member)
        if proposal_id:
            pinned = next((p for p in held if p.id == proposal_id), None)
            if pinned is None:
                raise EntityNotFoundException(NOTHING_TO_FORGET)
            return HeldMatch(pinned)
        if memory_id:
            stored, waiting = await self.match.execute(member.id, text or "", memory_id=memory_id), None
        else:
            stored, waiting = await self.match.execute_with_held(member.id, text or "", held)
        if stored is not None and stored.memory.user_id == member.id:
            return stored
        if waiting is not None:
            return waiting
        if stored is None:
            raise EntityNotFoundException(NOTHING_TO_FORGET)
        name = await speaker_name(self.user_repo, stored.memory.user_id)
        raise ZeroLeakViolationException(f"Only {name} can remove that memory. Nothing was forgotten.")

    async def execute(
        self, member: User, text: str, memory_id: Optional[str] = None, proposal_id: Optional[str] = None
    ) -> Union[AgentMemory, MemoryProposal]:
        found = await self.find(member, text, memory_id, proposal_id)
        score = f"{found.score:.3f}" if found.score is not None else None
        if isinstance(found, HeldMatch):
            logger.info("Memory forget | member=%s matched=%s score=%s held=True", member.id, found.proposal.id, score)
            return await self.withdraw.execute(found.proposal.id, member)
        logger.info("Memory forget | member=%s matched=%s score=%s", member.id, found.memory.id, score)
        return await self.retire.execute(found.memory.id, member)

    async def _held(self, member: User) -> List[MemoryProposal]:
        if self.proposal_repo is None or self.withdraw is None:
            return []
        return [p for p in await self.proposal_repo.list_pending_from_user(member.id) if p.origin == FROM_REMEMBER]


class MemoryOnCardUseCase:
    """
    What a `remember` or `forget` card shows (memory spec §2.1, §8): the stored memory it replaces,
    waits on or forgets, with who said it, which agent noted it and when. The memory's id, or the
    waiting change's, is pinned to the call, so the write runs on what the member saw, whatever the
    quote matches by then. A forget with nothing the member may forget gets no card: [refusal] is what
    the model is told.
    """

    def __init__(
        self,
        match: MatchMemoryUseCase,
        forget: ForgetUseCase,
        user_repo: IUserRepository,
        agent_repo: IAgentRepository,
        session_repo: Optional[ISessionRepository] = None,
    ):
        self.match = match
        self.forget = forget
        self.user_repo = user_repo
        self.agent_repo = agent_repo
        self.session_repo = session_repo

    async def execute(
        self, member: User, tool: str, arguments: Dict[str, Any]
    ) -> Tuple[Dict[str, Any], Dict[str, Any], Optional[str]]:
        found: Union[MemoryMatch, HeldMatch, None] = None
        if tool == "remember":
            replaces = str((arguments or {}).get("replaces") or "").strip()
            if replaces:
                found = await self.match.execute(member.id, replaces)
        elif tool == "forget":
            try:
                found = await self.forget.find(member, str((arguments or {}).get("memory") or ""))
            except (EntityNotFoundException, ZeroLeakViolationException) as refused:
                return {}, {}, refused.message
        if found is None:
            return {}, {}, None
        score = f"{found.score:.3f}" if found.score is not None else None
        if isinstance(found, HeldMatch):
            return await self._held(member, tool, found.proposal, score)

        stored = found.memory
        item = await MemoryItems(self.user_repo, self.agent_repo, member).item(stored)
        shown = {
            "stored": {
                "content": stored.content,
                "said_by": item.speaker_name or "Another member",
                "mine": item.is_mine,
                "agent": item.agent_name if stored.agent_id and item.agent_name != UNKNOWN_AGENT_NAME else None,
                "created_at": stored.created_at.isoformat() if stored.created_at else None,
            }
        }
        if stored.time_from_event and stored.event_starts_at:
            # Rebuilt from its event: the content is the title, and the phone adds the time (memory spec §7).
            start = stored.event_starts_at
            start = start if start.tzinfo else start.replace(tzinfo=timezone.utc)
            shown["stored"]["event_starts_at"] = start.isoformat()
            shown["stored"]["time_from_event"] = stored.time_from_event
        logger.info(
            "Memory card | member=%s tool=%s matched=%s score=%s mine=%s", member.id, tool, stored.id, score, item.is_mine
        )
        return shown, {"memory_id": stored.id}, None

    async def _held(
        self, member: User, tool: str, proposal: MemoryProposal, score: Optional[str]
    ) -> Tuple[Dict[str, Any], Dict[str, Any], Optional[str]]:
        """A forget card for a sentence that still waits: whose answer it waits on, and the change pinned."""
        items = MemoryItems(self.user_repo, self.agent_repo, member)
        chat = await self.session_repo.get_by_id(proposal.source_session_id) if self.session_repo and proposal.source_session_id else None
        agent = await self.agent_repo.get_by_id(chat.agent_id) if chat and chat.agent_id else None
        shown = {
            "stored": {
                "content": proposal.content,
                "said_by": await items.name(member.id) or "You",
                "mine": True,
                "agent": agent.name if agent else None,
                "created_at": proposal.created_at.isoformat() if proposal.created_at else None,
                "waiting_on": await speaker_name(self.user_repo, proposal.answerer_id),
            }
        }
        logger.info("Memory card | member=%s tool=%s matched=%s score=%s held=True", member.id, tool, proposal.id, score)
        return shown, {"proposal_id": proposal.id}, None