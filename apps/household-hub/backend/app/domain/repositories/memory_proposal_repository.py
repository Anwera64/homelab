from typing import List, Protocol

from app.domain.entities.memory_proposal import MemoryProposal


class IMemoryProposalRepository(Protocol):
    async def create(self, proposal: MemoryProposal) -> MemoryProposal:
        ...

    async def drop_pending_for(self, memory_id: str) -> List[str]:
        """Drops the memory's pending proposals, leaving answered ones; returns the ids dropped."""
        ...
