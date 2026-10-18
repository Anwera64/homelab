import logging
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Callable, Dict, Optional, Tuple

from app.domain.entities.memory import AgentMemory
from app.domain.entities.memory_proposal import CHANGED, FROM_REMEMBER, MemoryProposal
from app.domain.entities.user import User
from app.domain.exceptions import EntityNotFoundException, InvalidOperationException, ZeroLeakViolationException
from app.domain.repositories.agent_repository import IAgentRepository
from app.domain.repositories.memory_proposal_repository import IMemoryProposalRepository
from app.domain.repositories.memory_repository import IMemoryRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.repositories.user_repository import IUserRepository
from app.domain.use_cases.memories.embed_memories import EmbedMemoriesUseCase
from app.domain.use_cases.memories.list_memory_page import UNKNOWN_AGENT_NAME, MemoryItems
from app.domain.use_cases.memories.match_memory import MatchMemoryUseCase, MemoryMatch
from app.domain.use_cases.memories.retire_memory import RetireMemoryUseCase

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
    """What `remember` did: the memory it saved, and the stored one it replaced or was kept beside."""

    memory: AgentMemory
    replaced: Optional[AgentMemory] = None
    kept_beside: Optional[AgentMemory] = None
    # Who said the memory it was kept beside, and will be asked which one is right.
    speaker_name: Optional[str] = None
    proposal_id: Optional[str] = None


class RememberUseCase:
    """
    `remember`, once the member approved it or made it automatic (memory spec §2.1).

    The new sentence is saved as the member's. When the call names a stored memory it changes
    ([replaces], or the [memory_id] its card showed): the member's own is retired in its favour;
    another member's is never replaced. Theirs stays, the new one is a household memory, and they
    get a change to check, since only they can say which is right.
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
        theirs = stored is not None and stored.user_id != member.id

        scope = (scope or "personal").lower()
        category = (category or "fact").lower()
        new = AgentMemory(
            user_id=member.id,
            agent_id=agent_id,
            # Kept beside another member's memory, it is the household's: theirs is too.
            scope="household" if theirs else (scope if scope in SCOPES else "personal"),
            category=category if category in CATEGORIES else "fact",
            content=content,
            source_session_id=session_id,
            expires_at=_expiry(expires_at),
            origin="tool",
            created_at=self.clock(),
            updated_at=self.clock(),
        )
        done = Remembered(memory=new)
        async with self.uow:
            await self.memory_repo.create(new)
            if stored is not None:
                for dropped in await self.proposal_repo.drop_pending_for(stored.id):
                    logger.info(
                        "Memory proposal dropped | memory=%s proposal=%s reason=remembered", stored.id, dropped
                    )
            if stored is not None and not theirs:
                stored.is_active = False
                stored.replaced_by_id = new.id
                stored.retired_at = self.clock()
                await self.memory_repo.update(stored)
                done.replaced = stored
            elif theirs:
                proposal = MemoryProposal(
                    memory_id=stored.id,
                    content=content,
                    relation=CHANGED,
                    answerer_id=stored.user_id,
                    source_session_id=session_id,
                    source_user_id=member.id,
                    new_memory_id=new.id,
                    origin=FROM_REMEMBER,
                    created_at=self.clock(),
                )
                await self.proposal_repo.create(proposal)
                done.kept_beside = stored
                done.proposal_id = proposal.id
                done.speaker_name = await speaker_name(self.user_repo, stored.user_id)
            await self.uow.commit()

        outcome = "replaced" if done.replaced else "kept_beside" if done.kept_beside else "saved"
        text = f" text={content[:LOGGED_TEXT_CHARACTERS]!r}" if self.log_text else ""
        logger.info(
            "Memory remember | member=%s session=%s matched=%s score=%s outcome=%s new=%s proposal=%s%s",
            member.id, session_id or "none", stored.id if stored else None,
            f"{found.score:.3f}" if found and found.score is not None else None,
            outcome, new.id, done.proposal_id, text,
        )
        # Straight away, so recall can find it; the sweep fills it in later if the embedder is down.
        await self.embed_memories.execute([new])
        return done


class ForgetUseCase:
    """
    `forget`, once the member said yes on its card: it always asks (memory spec §2.1). Only the
    member's own memories can be forgotten, as with ✕ on the memory page; another's is refused by
    name, so the agent can say who can remove it.
    """

    def __init__(self, match: MatchMemoryUseCase, retire: RetireMemoryUseCase, user_repo: IUserRepository):
        self.match = match
        self.retire = retire
        self.user_repo = user_repo

    async def find(self, member: User, text: str, memory_id: Optional[str] = None) -> MemoryMatch:
        """The member's own memory the call means; refused, with why, when there is none."""
        found = await self.match.execute(member.id, text or "", memory_id=memory_id or None)
        if found is None:
            raise EntityNotFoundException(NOTHING_TO_FORGET)
        if found.memory.user_id != member.id:
            name = await speaker_name(self.user_repo, found.memory.user_id)
            raise ZeroLeakViolationException(f"Only {name} can remove that memory. Nothing was forgotten.")
        return found

    async def execute(self, member: User, text: str, memory_id: Optional[str] = None) -> AgentMemory:
        found = await self.find(member, text, memory_id)
        logger.info(
            "Memory forget | member=%s matched=%s score=%s",
            member.id, found.memory.id, f"{found.score:.3f}" if found.score is not None else None,
        )
        return await self.retire.execute(found.memory.id, member)


class MemoryOnCardUseCase:
    """
    What a `remember` or `forget` card shows (memory spec §2.1, §8): the stored memory it replaces,
    is kept beside or forgets, with who said it, which agent noted it and when. The memory's id is
    pinned to the call, so the write runs on the memory the member saw, whatever the quote matches by
    then. A forget with nothing the member may forget gets no card: [refusal] is what the model is told.
    """

    def __init__(
        self,
        match: MatchMemoryUseCase,
        forget: ForgetUseCase,
        user_repo: IUserRepository,
        agent_repo: IAgentRepository,
    ):
        self.match = match
        self.forget = forget
        self.user_repo = user_repo
        self.agent_repo = agent_repo

    async def execute(
        self, member: User, tool: str, arguments: Dict[str, Any]
    ) -> Tuple[Dict[str, Any], Dict[str, Any], Optional[str]]:
        found: Optional[MemoryMatch] = None
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
        logger.info(
            "Memory card | member=%s tool=%s matched=%s score=%s mine=%s",
            member.id, tool, stored.id, f"{found.score:.3f}" if found.score is not None else None, item.is_mine,
        )
        return shown, {"memory_id": stored.id}, None
