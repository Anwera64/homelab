from typing import Protocol, List, Optional
from app.domain.entities.user import User


class IUserRepository(Protocol):
    async def count(self) -> int:
        ...

    async def get_by_id(self, user_id: str) -> Optional[User]:
        ...

    async def list_all(self) -> List[User]:
        ...

    async def list_active(self) -> List[User]:
        ...

    async def create(self, user: User) -> User:
        ...

    async def update(self, user: User) -> User:
        ...

    async def delete(self, user_id: str) -> None:
        ...

    async def count_admins(self) -> int:
        ...

    async def get_other_admin(self, exclude_user_id: str) -> Optional[User]:
        ...
