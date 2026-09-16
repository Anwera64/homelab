from typing import List
from app.domain.entities.user import User
from app.domain.repositories.user_repository import IUserRepository


class ListMembersUseCase:
    def __init__(self, user_repo: IUserRepository):
        self.user_repo = user_repo

    async def execute(self) -> List[User]:
        return await self.user_repo.list_active()
