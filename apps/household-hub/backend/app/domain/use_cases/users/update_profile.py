from typing import Optional
from app.domain.entities.user import User
from app.domain.repositories.user_repository import IUserRepository
from app.domain.repositories.security_service import IPasswordHasher
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.exceptions import EntityNotFoundException


class UpdateProfileUseCase:
    def __init__(
        self,
        user_repo: IUserRepository,
        hasher: IPasswordHasher,
        uow: IUnitOfWork,
    ):
        self.user_repo = user_repo
        self.hasher = hasher
        self.uow = uow

    async def execute(
        self,
        current_user: User,
        full_name: Optional[str] = None,
        avatar_color: Optional[str] = None,
        password: Optional[str] = None,
    ) -> User:
        user = await self.user_repo.get_by_id(current_user.id)
        if not user:
            raise EntityNotFoundException("Member not found")

        async with self.uow:
            if full_name is not None:
                user.full_name = full_name
            if avatar_color is not None:
                user.avatar_color = avatar_color
            if password is not None:
                user.hashed_password = self.hasher.hash(password)

            updated = await self.user_repo.update(user)
            await self.uow.commit()

        return updated
