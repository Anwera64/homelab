from app.domain.entities.user import User
from app.domain.entities.space import Space
from app.domain.repositories.space_repository import ISpaceRepository
from app.domain.exceptions import EntityNotFoundException, ZeroLeakViolationException


class GetSpaceByIdUseCase:
    def __init__(self, space_repo: ISpaceRepository):
        self.space_repo = space_repo

    async def execute(self, space_id: str, current_user: User) -> Space:
        space = await self.space_repo.get_by_id(space_id)
        if not space:
            raise EntityNotFoundException("Space not found")

        if not space.can_access(current_user.id):
            raise ZeroLeakViolationException("Zero-Leak Privacy violation: Personal spaces are strictly private to their owner.")

        return space
