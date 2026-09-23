from typing import Protocol, Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select

from app.data.models.llm_model_model import LLMModelModel


class ILLMModelDataSource(Protocol):
    async def get_default(self) -> Optional[LLMModelModel]:
        ...

    async def get_by_id(self, model_id: str) -> Optional[LLMModelModel]:
        ...

    async def create(self, model: LLMModelModel) -> LLMModelModel:
        ...

    async def update(self, model: LLMModelModel) -> LLMModelModel:
        ...


class SqliteLLMModelDataSource(ILLMModelDataSource):
    def __init__(self, session: AsyncSession):
        self.session = session

    async def get_default(self) -> Optional[LLMModelModel]:
        res = await self.session.execute(select(LLMModelModel).where(LLMModelModel.is_default.is_(True)))
        return res.scalars().first()

    async def get_by_id(self, model_id: str) -> Optional[LLMModelModel]:
        res = await self.session.execute(select(LLMModelModel).where(LLMModelModel.id == model_id))
        return res.scalars().first()

    async def create(self, model: LLMModelModel) -> LLMModelModel:
        self.session.add(model)
        await self.session.flush()
        return model

    async def update(self, model: LLMModelModel) -> LLMModelModel:
        await self.session.flush()
        return model
