from app.domain.entities.agent import AgentPersonality
from app.domain.exceptions import ModelNotConfiguredException
from app.domain.repositories.llm_model_repository import ILLMModelRepository


class ResolveAgentModelUseCase:
    """Turns an agent into the name the inference server knows. Nothing else needs to know that name."""

    def __init__(self, model_repo: ILLMModelRepository):
        self.model_repo = model_repo

    async def for_agent(self, agent: AgentPersonality) -> str:
        if agent.llm_model_id is None:
            return await self.default()
        model = await self.model_repo.get_by_id(agent.llm_model_id)
        if model is None:
            raise ModelNotConfiguredException(
                f"Agent '{agent.name}' is set to a model that no longer exists."
            )
        return model.provider_model

    async def default(self) -> str:
        model = await self.model_repo.get_default()
        if model is None:
            raise ModelNotConfiguredException("No household default model is configured.")
        return model.provider_model
