from app.domain.entities.space import Space, DEFAULT_SHARED_SETTINGS
from app.domain.repositories.space_repository import ISpaceRepository
from app.domain.repositories.unit_of_work import IUnitOfWork


class GetSharedSpaceUseCase:
    def __init__(self, space_repo: ISpaceRepository, uow: IUnitOfWork):
        self.space_repo = space_repo
        self.uow = uow

    async def execute(self) -> Space:
        space = await self.space_repo.get_shared()
        if not space:
            async with self.uow:
                space = Space(
                    name="Household Shared Hub",
                    type="shared",
                    owner_id=None,
                    settings=DEFAULT_SHARED_SETTINGS,
                )
                space = await self.space_repo.create(space)
                await self.uow.commit()
        return space
