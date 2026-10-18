import logging

from app.domain.entities.memory_proposal import FROM_REMEMBER, PENDING, WITHDRAWN, MemoryProposal
from app.domain.entities.user import User
from app.domain.exceptions import EntityNotFoundException, ProposalStateException, ZeroLeakViolationException
from app.domain.repositories.memory_proposal_repository import IMemoryProposalRepository
from app.domain.repositories.memory_repository import IMemoryRepository
from app.domain.repositories.unit_of_work import IUnitOfWork

logger = logging.getLogger(__name__)


async def _askers_remember(
    proposal_repo: IMemoryProposalRepository, proposal_id: str, member: User, action: str
) -> MemoryProposal:
    """Only the member who asked to `remember` can take it back; reflection's changes have no asker's sentence."""
    proposal = await proposal_repo.get_by_id(proposal_id)
    if proposal is None:
        raise EntityNotFoundException("Change not found")
    if proposal.source_user_id != member.id:
        logger.info("Memory change %s refused | member=%s proposal=%s reason=not_theirs", action, member.id, proposal_id)
        raise ZeroLeakViolationException("Only the member who asked to remember this can take it back.")
    if proposal.origin != FROM_REMEMBER:
        raise ProposalStateException("This change wasn't asked for, so there's nothing to take back.")
    return proposal


class WithdrawChangeUseCase:
    """✕ on a waiting card (memory spec §2.1): the member takes back a sentence still waiting on someone
    else's answer. Nothing was saved, so nothing is removed; the change just stops waiting."""

    def __init__(self, proposal_repo: IMemoryProposalRepository, memory_repo: IMemoryRepository, uow: IUnitOfWork):
        self.proposal_repo = proposal_repo
        self.memory_repo = memory_repo
        self.uow = uow

    async def execute(self, proposal_id: str, member: User) -> MemoryProposal:
        proposal = await _askers_remember(self.proposal_repo, proposal_id, member, "withdraw")
        if proposal.state != PENDING:
            raise ProposalStateException("This change was already answered.")
        async with self.uow:
            proposal.state = WITHDRAWN
            proposal = await self.proposal_repo.update(proposal)
            await self.uow.commit()
        logger.info("Memory change withdrawn | member=%s proposal=%s memory=%s", member.id, proposal.id, proposal.memory_id)
        return proposal


class RestoreChangeUseCase:
    """Undo after ✕ on a waiting card: it waits again, as long as the memory it waits on is still there."""

    def __init__(self, proposal_repo: IMemoryProposalRepository, memory_repo: IMemoryRepository, uow: IUnitOfWork):
        self.proposal_repo = proposal_repo
        self.memory_repo = memory_repo
        self.uow = uow

    async def execute(self, proposal_id: str, member: User) -> MemoryProposal:
        proposal = await _askers_remember(self.proposal_repo, proposal_id, member, "restore")
        if proposal.state != WITHDRAWN:
            raise ProposalStateException("This change wasn't taken back.")
        stored = await self.memory_repo.get_by_id(proposal.memory_id)
        if stored is None or not stored.is_active:
            raise ProposalStateException("The memory it waited on is gone, so it can't come back.")
        async with self.uow:
            proposal.state = PENDING
            proposal = await self.proposal_repo.update(proposal)
            await self.uow.commit()
        logger.info("Memory change restored | member=%s proposal=%s memory=%s", member.id, proposal.id, proposal.memory_id)
        return proposal
