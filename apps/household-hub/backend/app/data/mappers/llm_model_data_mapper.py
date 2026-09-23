from app.domain.entities.llm_model import LLMModel
from app.data.models.llm_model_model import LLMModelModel


class LLMModelDataMapper:
    @staticmethod
    def to_domain(model: LLMModelModel) -> LLMModel:
        return LLMModel(
            id=model.id,
            provider_model=model.provider_model,
            display_name=model.display_name or "",
            is_default=model.is_default,
            created_at=model.created_at,
            updated_at=model.updated_at,
        )

    @staticmethod
    def to_model(entity: LLMModel) -> LLMModelModel:
        return LLMModelModel(
            id=entity.id,
            provider_model=entity.provider_model,
            display_name=entity.display_name,
            is_default=entity.is_default,
            created_at=entity.created_at,
            updated_at=entity.updated_at,
        )
