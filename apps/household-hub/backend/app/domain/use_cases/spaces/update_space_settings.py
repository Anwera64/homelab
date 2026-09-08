from typing import Any, Dict
from app.domain.entities.user import User
from app.domain.entities.space import Space
from app.domain.repositories.space_repository import ISpaceRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.exceptions import EntityNotFoundException, ZeroLeakViolationException


class UpdateSpaceSettingsUseCase:
    def __init__(self, space_repo: ISpaceRepository, uow: IUnitOfWork):
        self.space_repo = space_repo
        self.uow = uow

    async def execute(self, space_id: str, settings: Dict[str, Any], current_user: User) -> Space:
        space = await self.space_repo.get_by_id(space_id)
        if not space:
            raise EntityNotFoundException("Space not found")

        if space.type == "personal" and space.owner_id != current_user.id:
            raise ZeroLeakViolationException("Zero-Leak Privacy violation: Cannot modify another member's personal space.")

        async with self.uow:
            space.settings = settings
            updated = await self.space_repo.update(space)
            await self.uow.commit()

        return updated
