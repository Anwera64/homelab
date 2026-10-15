from typing import List, Protocol

from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.data.models.memory_proposal_model import MemoryProposalModel

_PENDING = "pending"
_DROPPED = "dropped"


class IMemoryProposalDataSource(Protocol):
    async def create(self, model: MemoryProposalModel) -> MemoryProposalModel:
        ...

    async def drop_pending_for(self, memory_id: str) -> List[str]:
        ...


class SqliteMemoryProposalDataSource(IMemoryProposalDataSource):
    def __init__(self, session: AsyncSession):
        self.session = session

    async def create(self, model: MemoryProposalModel) -> MemoryProposalModel:
        self.session.add(model)
        await self.session.flush()
        return model

    async def drop_pending_for(self, memory_id: str) -> List[str]:
        pending = (
            MemoryProposalModel.memory_id == memory_id,
            MemoryProposalModel.state == _PENDING,
        )
        ids = list((await self.session.execute(select(MemoryProposalModel.id).where(*pending))).scalars())
        if ids:
            await self.session.execute(
                update(MemoryProposalModel)
                .where(MemoryProposalModel.id.in_(ids))
                .values(state=_DROPPED)
                .execution_options(synchronize_session="fetch")
            )
            await self.session.flush()
        return ids
