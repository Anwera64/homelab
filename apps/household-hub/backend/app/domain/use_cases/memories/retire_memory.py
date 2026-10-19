import logging
from datetime import datetime, timezone
from typing import Callable, List, Optional

from app.domain.entities.memory import AgentMemory
from app.domain.entities.memory_proposal import BOTH, FROM_REMEMBER
from app.domain.entities.user import User
from app.domain.exceptions import EntityNotFoundException, ZeroLeakViolationException
from app.domain.repositories.memory_proposal_repository import IMemoryProposalRepository
from app.domain.repositories.memory_repository import IMemoryRepository
from app.domain.repositories.session_repository import ISessionRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.use_cases.memories.answer_proposal import memory_from_proposal
from app.domain.use_cases.memories.embed_memories import EmbedMemoriesUseCase

logger = logging.getLogger(__name__)


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


class RetireMemoryUseCase:
    """✕ on the memory page (memory spec §3.1). The memory is made inactive, never deleted, so Undo can bring it back.

    Only the member who said it may remove it, whatever its scope: an admin can't remove another member's
    household memory either.
    """

    def __init__(
        self,
        memory_repo: IMemoryRepository,
        uow: IUnitOfWork,
        clock: Callable[[], datetime] = _utc_now,
        proposal_repo: Optional[IMemoryProposalRepository] = None,
        session_repo: Optional[ISessionRepository] = None,
        embed_memories: Optional[EmbedMemoriesUseCase] = None,
    ):
        self.memory_repo = memory_repo
        self.uow = uow
        self.clock = clock
        # A removed memory's pending proposals are dropped, not answered (memory spec §3.5), except
        # a `remember` waiting on it: nothing conflicts any more, so the sentence its asker gave is saved.
        self.proposal_repo = proposal_repo
        self.session_repo = session_repo
        self.embed_memories = embed_memories

    async def execute(self, memory_id: str, current_user: User) -> AgentMemory:
        memory = await self.memory_repo.get_by_id(memory_id)
        if not memory:
            raise EntityNotFoundException("Memory not found")
        if memory.user_id != current_user.id:
            logger.info("Memory remove refused | member=%s memory=%s reason=not_theirs", current_user.id, memory_id)
            raise ZeroLeakViolationException("Only the member who said a memory can remove it.")

        saved: List[AgentMemory] = []
        async with self.uow:
            memory.is_active = False
            memory.retired_at = self.clock()
            memory = await self.memory_repo.update(memory)
            if self.proposal_repo and self.session_repo:
                saved = await save_held_on(memory, self.proposal_repo, self.memory_repo, self.session_repo, self.clock)
            dropped = await self.proposal_repo.drop_pending_for(memory_id) if self.proposal_repo else []
            await self.uow.commit()
        for proposal_id in dropped:
            logger.info(
                "Memory proposal dropped | memory=%s proposal=%s reason=memory_removed", memory_id, proposal_id
            )
        logger.info("Memory removed | member=%s memory=%s", current_user.id, memory_id)
        if saved and self.embed_memories:
            await self.embed_memories.execute(saved)
        return memory


async def save_held_on(
    stored: AgentMemory,
    proposal_repo: IMemoryProposalRepository,
    memory_repo: IMemoryRepository,
    session_repo: ISessionRepository,
    clock: Callable[[], datetime],
    reason: str = "memory_removed",
) -> List[AgentMemory]:
    """Saves each `remember` waiting on [stored] as if it were kept beside it, once no one is left to
    answer it: [stored] was removed, or whoever said it left the household. Inside the caller's unit of work."""
    saved: List[AgentMemory] = []
    for proposal in await proposal_repo.list_pending_for_answerer(stored.user_id):
        if proposal.memory_id != stored.id or proposal.origin != FROM_REMEMBER:
            continue
        new = await memory_repo.create(await memory_from_proposal(proposal, stored, session_repo))
        proposal.new_memory_id = new.id
        proposal.state = BOTH
        proposal.answered_at = clock()
        await proposal_repo.update(proposal)
        saved.append(new)
        logger.info(
            "Memory proposal saved | memory=%s proposal=%s new_memory=%s reason=%s", stored.id, proposal.id, new.id, reason
        )
    return saved
