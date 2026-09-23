from app.domain.repositories.llm_model_repository import ILLMModelRepository
from app.domain.repositories.unit_of_work import IUnitOfWork


class SyncDefaultModelUseCase:
    """Makes the household default name the configured model. Switching models is this and a restart."""

    def __init__(self, model_repo: ILLMModelRepository, uow: IUnitOfWork):
        self.model_repo = model_repo
        self.uow = uow

    async def execute(self, provider_model: str) -> None:
        current = await self.model_repo.get_default()
        if current is not None and current.provider_model == provider_model:
            return
        async with self.uow:
            await self.model_repo.set_default_provider_model(provider_model)
            await self.uow.commit()
