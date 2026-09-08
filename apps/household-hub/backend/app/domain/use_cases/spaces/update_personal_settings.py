from typing import Any, Dict
from app.domain.entities.user import User
from app.domain.entities.space import Space
from app.domain.repositories.space_repository import ISpaceRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.exceptions import EntityNotFoundException


class UpdatePersonalSettingsUseCase:
    def __init__(self, space_repo: ISpaceRepository, uow: IUnitOfWork):
        self.space_repo = space_repo
        self.uow = uow

    async def execute(self, current_user: User, settings: Dict[str, Any]) -> Space:
        space = await self.space_repo.get_by_owner_id(current_user.id)
        if not space:
            raise EntityNotFoundException("Personal space not found")

        async with self.uow:
            space.settings = settings
            updated = await self.space_repo.update(space)
            await self.uow.commit()

        return updated
