from app.domain.entities.user import User
from app.domain.entities.space import Space
from app.domain.repositories.space_repository import ISpaceRepository
from app.domain.exceptions import EntityNotFoundException


class GetPersonalSpaceUseCase:
    def __init__(self, space_repo: ISpaceRepository):
        self.space_repo = space_repo

    async def execute(self, user: User) -> Space:
        space = await self.space_repo.get_by_owner_id(user.id)
        if not space:
            raise EntityNotFoundException("Personal space not found")
        return space
