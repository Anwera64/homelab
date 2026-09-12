from typing import List

from app.domain.entities.user import User
from app.domain.repositories.user_repository import IUserRepository


class ListPublicMembersUseCase:
    """
    Who can sign in, for the profile picker, before anyone has. It reveals names to anyone who can
    reach the hub — acceptable on a LAN and Tailscale, and a deliberate choice.
    """

    def __init__(self, user_repo: IUserRepository):
        self.user_repo = user_repo

    async def execute(self) -> List[User]:
        return await self.user_repo.list_active()
