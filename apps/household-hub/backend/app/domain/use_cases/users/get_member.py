from app.domain.entities.user import User
from app.domain.repositories.user_repository import IUserRepository
from app.domain.exceptions import EntityNotFoundException


class GetMemberUseCase:
    def __init__(self, user_repo: IUserRepository):
        self.user_repo = user_repo

    async def execute(self, user_id: str) -> User:
        user = await self.user_repo.get_by_id(user_id)
        if not user:
            raise EntityNotFoundException("Member not found")
        return user
