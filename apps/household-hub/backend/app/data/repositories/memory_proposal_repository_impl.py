from typing import List, Optional

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

    async def get_by_id(self, proposal_id: str) -> Optional[MemoryProposal]:
        model = await self.data_source.get_by_id(proposal_id)
        return self.mapper.to_domain(model) if model else None

    async def get_many(self, proposal_ids: List[str]) -> List[MemoryProposal]:
        return [self.mapper.to_domain(m) for m in await self.data_source.get_many(proposal_ids)]

    async def update(self, proposal: MemoryProposal) -> MemoryProposal:
        updated = await self.data_source.update(self.mapper.to_model(proposal))
        return self.mapper.to_domain(updated)

    async def drop_pending_for(self, memory_id: str) -> List[str]:
        return await self.data_source.drop_pending_for(memory_id)

    async def list_pending_for_answerer(self, user_id: str) -> List[MemoryProposal]:
        return [self.mapper.to_domain(m) for m in await self.data_source.list_pending_for_answerer(user_id)]

    async def list_pending_from_user(self, user_id: str) -> List[MemoryProposal]:
        return [self.mapper.to_domain(m) for m in await self.data_source.list_pending_from_user(user_id)]
