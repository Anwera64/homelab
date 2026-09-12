from typing import Optional
from app.domain.entities.user import User
from app.domain.repositories.user_repository import IUserRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.exceptions import EntityNotFoundException


class UpdateProfileUseCase:
    """Name and colour. Changing a PIN is its own flow, because it signs out other devices."""

    def __init__(
        self,
        user_repo: IUserRepository,
        uow: IUnitOfWork,
    ):
        self.user_repo = user_repo
        self.uow = uow

    async def execute(
        self,
        current_user: User,
        full_name: Optional[str] = None,
        avatar_color: Optional[str] = None,
    ) -> User:
        user = await self.user_repo.get_by_id(current_user.id)
        if not user:
            raise EntityNotFoundException("Member not found")

        async with self.uow:
            if full_name is not None:
                user.full_name = full_name
            if avatar_color is not None:
                user.avatar_color = avatar_color

            updated = await self.user_repo.update(user)
            await self.uow.commit()

        return updated
