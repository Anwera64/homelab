from typing import List

from app.data.datasources.memory_proposal_data_source import IMemoryProposalDataSource
from app.data.mappers.memory_proposal_data_mapper import MemoryProposalDataMapper
from app.domain.entities.memory_proposal import MemoryProposal
from app.domain.repositories.memory_proposal_repository import IMemoryProposalRepository


class MemoryProposalRepositoryImpl(IMemoryProposalRepository):
    def __init__(self, data_source: IMemoryProposalDataSource, mapper: MemoryProposalDataMapper):
        self.data_source = data_source
        self.mapper = mapper

    async def create(self, proposal: MemoryProposal) -> MemoryProposal:
        created = await self.data_source.create(self.mapper.to_model(proposal))
        return self.mapper.to_domain(created)

    async def drop_pending_for(self, memory_id: str) -> List[str]:
        return await self.data_source.drop_pending_for(memory_id)
