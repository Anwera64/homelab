import logging
from datetime import datetime, timezone
from typing import Callable, Optional

from app.domain.entities.memory import AgentMemory
from app.domain.entities.memory_proposal import (
    ANSWERS,
    BOTH,
    FROM_REMEMBER,
    KEPT,
    PENDING,
    REPLACED,
    WITHDRAWN,
    MemoryProposal,
)
from app.domain.entities.user import User
from app.domain.exceptions import (
    ChangeWithdrawnException,
    EntityNotFoundException,
    ProposalStateException,
    ZeroLeakViolationException,
)
from app.domain.repositories.memory_proposal_repository import IMemoryProposalRepository
from app.domain.repositories.memory_repository import IMemoryRepository
from app.domain.repositories.session_repository import ISessionRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.repositories.user_repository import IUserRepository
from app.domain.use_cases.memories.embed_memories import EmbedMemoriesUseCase

logger = logging.getLogger(__name__)

LOGGED_TEXT_CHARACTERS = 500

# The three buttons on a change card (memory spec §6.1), as the phone sends them.
KEEP_STORED = "keep_stored"
KEEP_BOTH = "keep_both"
USE_NEW = "use_new"
_STATE_FOR = {KEEP_STORED: KEPT, KEEP_BOTH: BOTH, USE_NEW: REPLACED}


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


async def _own_proposal(
    proposal_repo: IMemoryProposalRepository, proposal_id: str, member: User, action: str
) -> MemoryProposal:
    """Only the member who said the stored memory answers its changes (memory spec §6.1)."""
    proposal = await proposal_repo.get_by_id(proposal_id)
    if proposal is None:
        raise EntityNotFoundException("Change not found")
    if proposal.answerer_id != member.id:
        logger.info(
            "Memory proposal %s refused | member=%s proposal=%s reason=not_theirs", action, member.id, proposal_id
        )
        raise ZeroLeakViolationException("Only the member who said this memory can choose.")
    return proposal


async def memory_from_proposal(
    proposal: MemoryProposal, stored: AgentMemory, session_repo: ISessionRepository
) -> AgentMemory:
    """The memory a change saves (Keep both, Use new, or a `remember` no one is left to answer): the new
    sentence, said by whoever said it in the chat, with the stored memory's scope and category."""
    chat = await session_repo.get_by_id(proposal.source_session_id) if proposal.source_session_id else None
    return AgentMemory(
        user_id=proposal.source_user_id or proposal.answerer_id,
        agent_id=chat.agent_id if chat else None,
        scope=stored.scope,
        category=stored.category,
        content=proposal.content,
        source_session_id=proposal.source_session_id,
        expires_at=proposal.expires_at,
        # Asked for with `remember`, so its page row says "You asked" to whoever asked.
        origin="tool" if proposal.origin == FROM_REMEMBER else "reflection",
    )


class AnswerProposalUseCase:
    """Keep stored, Keep both or Use new on a change to check (memory spec §6.1).

    Keep both and Use new save the new sentence as a memory said by whoever said it in the chat, with
    the stored memory's scope. Use new retires the stored memory in its favour. A change `remember`
    raised is answered the same way: its sentence was only held until now. One its asker took back is
    refused by name.
    """

    def __init__(
        self,
        proposal_repo: IMemoryProposalRepository,
        memory_repo: IMemoryRepository,
        session_repo: ISessionRepository,
        uow: IUnitOfWork,
        embed_memories: EmbedMemoriesUseCase,
        clock: Callable[[], datetime] = _utc_now,
        log_text: bool = False,
        user_repo: Optional[IUserRepository] = None,
    ):
        self.proposal_repo = proposal_repo
        self.memory_repo = memory_repo
        self.session_repo = session_repo
        self.uow = uow
        self.embed_memories = embed_memories
        self.clock = clock
        self.log_text = log_text
        self.user_repo = user_repo

    async def execute(self, proposal_id: str, answer: str, member: User) -> MemoryProposal:
        if answer not in _STATE_FOR:
            raise ValueError(f"Unknown answer: {answer}")
        proposal = await _own_proposal(self.proposal_repo, proposal_id, member, "answer")
        if proposal.state == WITHDRAWN:
            raise ChangeWithdrawnException(f"{await self._asker_name(proposal)} took this back.")
        if proposal.state != PENDING:
            raise ProposalStateException("This change was already answered.")
        stored = await self.memory_repo.get_by_id(proposal.memory_id)
        if stored is None:
            raise EntityNotFoundException("Memory not found")

        now = self.clock()
        new: Optional[AgentMemory] = None
        async with self.uow:
            if answer != KEEP_STORED:
                new = await self.memory_repo.create(await memory_from_proposal(proposal, stored, self.session_repo))
                proposal.new_memory_id = new.id
            if answer == USE_NEW:
                stored.is_active = False
                stored.replaced_by_id = new.id
                stored.retired_at = now
                await self.memory_repo.update(stored)
            proposal.state = _STATE_FOR[answer]
            proposal.answered_at = now
            proposal = await self.proposal_repo.update(proposal)
            await self.uow.commit()

        text = f" text={proposal.content[:LOGGED_TEXT_CHARACTERS]!r}" if self.log_text else ""
        logger.info(
            "Memory proposal answered | member=%s proposal=%s memory=%s answer=%s new_memory=%s%s",
            member.id, proposal.id, proposal.memory_id, proposal.state, proposal.new_memory_id, text,
        )
        if new is not None:
            # Straight away, so recall can find it; the sweep fills it in later if the embedder is down.
            await self.embed_memories.execute([new])
        return proposal

    async def _asker_name(self, proposal: MemoryProposal) -> str:
        asker = await self.user_repo.get_by_id(proposal.source_user_id) if self.user_repo and proposal.source_user_id else None
        return (asker.full_name if asker else "") or "Whoever asked"


class UndoProposalAnswerUseCase:
    """Undo after answering (memory spec §6.1): the change is open again and everything is as before.

    The memory the answer saved is deleted rather than retired, so it can't come back through ✕'s Undo.
    """

    def __init__(
        self,
        proposal_repo: IMemoryProposalRepository,
        memory_repo: IMemoryRepository,
        uow: IUnitOfWork,
        clock: Callable[[], datetime] = _utc_now,
    ):
        self.proposal_repo = proposal_repo
        self.memory_repo = memory_repo
        self.uow = uow
        self.clock = clock

    async def execute(self, proposal_id: str, member: User) -> MemoryProposal:
        proposal = await _own_proposal(self.proposal_repo, proposal_id, member, "undo")
        if proposal.state not in ANSWERS:
            raise ProposalStateException("This change has no answer to undo.")
        answer = proposal.state
        removed = proposal.new_memory_id

        async with self.uow:
            if answer == REPLACED:
                stored = await self.memory_repo.get_by_id(proposal.memory_id)
                if stored is not None:
                    stored.is_active = True
                    stored.replaced_by_id = None
                    stored.retired_at = None
                    await self.memory_repo.update(stored)
            proposal.state = PENDING
            proposal.answered_at = None
            proposal.new_memory_id = None
            proposal = await self.proposal_repo.update(proposal)
            if removed:
                await self.memory_repo.delete(removed)
            await self.uow.commit()

        logger.info(
            "Memory proposal undone | member=%s proposal=%s memory=%s answer=%s removed_memory=%s",
            member.id, proposal.id, proposal.memory_id, answer, removed,
        )
        return proposal
