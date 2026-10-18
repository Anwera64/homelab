from typing import List, Optional, Protocol

from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.data.models.memory_proposal_model import MemoryProposalModel

_PENDING = "pending"
_DROPPED = "dropped"


class IMemoryProposalDataSource(Protocol):
    async def create(self, model: MemoryProposalModel) -> MemoryProposalModel:
        ...

    async def get_by_id(self, proposal_id: str) -> Optional[MemoryProposalModel]:
        ...

    async def get_many(self, proposal_ids: List[str]) -> List[MemoryProposalModel]:
        ...

    async def update(self, model: MemoryProposalModel) -> MemoryProposalModel:
        ...

    async def drop_pending_for(self, memory_id: str, origin: Optional[str] = None) -> List[str]:
        ...

    async def list_pending_for_answerer(self, user_id: str) -> List[MemoryProposalModel]:
        ...

    async def list_pending_from_user(self, user_id: str) -> List[MemoryProposalModel]:
        ...


class SqliteMemoryProposalDataSource(IMemoryProposalDataSource):
    def __init__(self, session: AsyncSession):
        self.session = session

    async def create(self, model: MemoryProposalModel) -> MemoryProposalModel:
        self.session.add(model)
        await self.session.flush()
        return model

    async def get_by_id(self, proposal_id: str) -> Optional[MemoryProposalModel]:
        return await self.session.get(MemoryProposalModel, proposal_id)

    async def get_many(self, proposal_ids: List[str]) -> List[MemoryProposalModel]:
        if not proposal_ids:
            return []
        stmt = select(MemoryProposalModel).where(MemoryProposalModel.id.in_(proposal_ids))
        return list((await self.session.execute(stmt)).scalars())

    async def update(self, model: MemoryProposalModel) -> MemoryProposalModel:
        merged = await self.session.merge(model)
        await self.session.flush()
        return merged

    async def drop_pending_for(self, memory_id: str, origin: Optional[str] = None) -> List[str]:
        pending = (MemoryProposalModel.memory_id == memory_id, MemoryProposalModel.state == _PENDING)
        if origin is not None:
            pending += (MemoryProposalModel.origin == origin,)
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

    async def list_pending_for_answerer(self, user_id: str) -> List[MemoryProposalModel]:
        stmt = (
            select(MemoryProposalModel)
            .where(MemoryProposalModel.answerer_id == user_id, MemoryProposalModel.state == _PENDING)
            .order_by(MemoryProposalModel.created_at.desc())
        )
        return list((await self.session.execute(stmt)).scalars())

    async def list_pending_from_user(self, user_id: str) -> List[MemoryProposalModel]:
        stmt = (
            select(MemoryProposalModel)
            .where(
                MemoryProposalModel.source_user_id == user_id,
                MemoryProposalModel.answerer_id != user_id,
                MemoryProposalModel.state == _PENDING,
            )
            .order_by(MemoryProposalModel.created_at.desc())
        )
        return list((await self.session.execute(stmt)).scalars())
