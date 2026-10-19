from typing import List, Optional, Protocol

from app.domain.entities.memory_proposal import MemoryProposal


class IMemoryProposalRepository(Protocol):
    async def create(self, proposal: MemoryProposal) -> MemoryProposal:
        ...

    async def get_by_id(self, proposal_id: str) -> Optional[MemoryProposal]:
        ...

    async def get_many(self, proposal_ids: List[str]) -> List[MemoryProposal]:
        ...

    async def update(self, proposal: MemoryProposal) -> MemoryProposal:
        ...

    async def drop_pending_for(self, memory_id: str, origin: Optional[str] = None) -> List[str]:
        """Drops the memory's pending proposals, only those of [origin] when given, leaving answered
        ones; returns the ids dropped."""
        ...

    async def list_pending_for_answerer(self, user_id: str) -> List[MemoryProposal]:
        """The member's changes to check, newest first."""
        ...

    async def list_pending_from_user(self, user_id: str) -> List[MemoryProposal]:
        """Pending changes raised in the member's chats that someone else answers, newest first."""
        ...
