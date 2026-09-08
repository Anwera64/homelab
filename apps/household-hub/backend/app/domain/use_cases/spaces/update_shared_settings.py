from typing import Any, Dict
from app.domain.entities.space import Space, DEFAULT_SHARED_SETTINGS
from app.domain.repositories.space_repository import ISpaceRepository
from app.domain.repositories.unit_of_work import IUnitOfWork


class UpdateSharedSettingsUseCase:
    def __init__(self, space_repo: ISpaceRepository, uow: IUnitOfWork):
        self.space_repo = space_repo
        self.uow = uow

    async def execute(self, settings: Dict[str, Any]) -> Space:
        space = await self.space_repo.get_shared()
        async with self.uow:
            if not space:
                space = Space(
                    name="Household Shared Hub",
                    type="shared",
                    owner_id=None,
                    settings=settings,
                )
                space = await self.space_repo.create(space)
            else:
                space.settings = settings
                space = await self.space_repo.update(space)
            await self.uow.commit()

        return space
