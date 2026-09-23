from typing import Optional
from app.domain.entities.llm_model import LLMModel
from app.domain.repositories.llm_model_repository import ILLMModelRepository
from app.data.datasources.llm_model_data_source import ILLMModelDataSource
from app.data.mappers.llm_model_data_mapper import LLMModelDataMapper


class LLMModelRepositoryImpl(ILLMModelRepository):
    def __init__(self, data_source: ILLMModelDataSource, mapper: LLMModelDataMapper):
        self.data_source = data_source
        self.mapper = mapper

    async def get_default(self) -> Optional[LLMModel]:
        model = await self.data_source.get_default()
        return self.mapper.to_domain(model) if model else None

    async def get_by_id(self, model_id: str) -> Optional[LLMModel]:
        model = await self.data_source.get_by_id(model_id)
        return self.mapper.to_domain(model) if model else None

    async def set_default_provider_model(self, provider_model: str) -> LLMModel:
        model = await self.data_source.get_default()
        if model is None:
            created = await self.data_source.create(
                self.mapper.to_model(
                    LLMModel(provider_model=provider_model, display_name=provider_model, is_default=True)
                )
            )
            return self.mapper.to_domain(created)
        model.provider_model = provider_model
        model.display_name = provider_model
        return self.mapper.to_domain(await self.data_source.update(model))
