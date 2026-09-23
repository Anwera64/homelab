from typing import Protocol, Optional
from app.domain.entities.llm_model import LLMModel


class ILLMModelRepository(Protocol):
    async def get_default(self) -> Optional[LLMModel]:
        ...

    async def get_by_id(self, model_id: str) -> Optional[LLMModel]:
        ...

    async def set_default_provider_model(self, provider_model: str) -> LLMModel:
        """Point the household default at `provider_model`, creating the default row if there is none."""
        ...
