from typing import Optional

from app.domain.entities.user import User, DEFAULT_AVATAR_COLOR
from app.domain.entities.space import Space, DEFAULT_PERSONAL_SETTINGS
from app.domain.repositories.user_repository import IUserRepository
from app.domain.repositories.space_repository import ISpaceRepository
from app.domain.repositories.security_service import IPasswordHasher
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.exceptions import InvalidOperationException


class CreateMemberUseCase:
    """
    Adds a member with the PIN they chose. No endpoint calls it yet: redeeming an invite will.
    """

    def __init__(
        self,
        user_repo: IUserRepository,
        space_repo: ISpaceRepository,
        hasher: IPasswordHasher,
        uow: IUnitOfWork,
    ):
        self.user_repo = user_repo
        self.space_repo = space_repo
        self.hasher = hasher
        self.uow = uow

    async def execute(
        self,
        full_name: str,
        pin: str,
        avatar_color: Optional[str] = None,
        is_admin: bool = False,
    ) -> User:
        # The profile picker tells members apart by name alone.
        active = await self.user_repo.list_active()
        if any(m.full_name.casefold() == full_name.casefold() for m in active):
            raise InvalidOperationException("Someone in the household already has that name.")

        async with self.uow:
            user = User(
                full_name=full_name,
                avatar_color=avatar_color or DEFAULT_AVATAR_COLOR,
                hashed_pin=self.hasher.hash(pin),
                is_admin=is_admin,
                is_active=True,
            )
            created_user = await self.user_repo.create(user)

            # Provision personal space
            personal_space = Space(
                name=f"{full_name}'s Space",
                type="personal",
                owner_id=created_user.id,
                settings=DEFAULT_PERSONAL_SETTINGS,
            )
            created_space = await self.space_repo.create(personal_space)
            created_user.personal_space_id = created_space.id
            await self.user_repo.update(created_user)

            await self.uow.commit()

        return created_user
