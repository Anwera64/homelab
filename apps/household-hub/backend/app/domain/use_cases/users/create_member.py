from app.domain.entities.user import User
from app.domain.entities.space import Space, DEFAULT_PERSONAL_SETTINGS
from app.domain.repositories.user_repository import IUserRepository
from app.domain.repositories.space_repository import ISpaceRepository
from app.domain.repositories.security_service import IPasswordHasher
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.exceptions import InvalidOperationException


class CreateMemberUseCase:
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
        username: str,
        email: str,
        password: str,
        full_name: str,
        avatar_color: str = "#4F46E5",
        is_admin: bool = False,
    ) -> User:
        existing = await self.user_repo.get_by_username_or_email(username, email)
        if existing:
            raise InvalidOperationException("A user with this username or email already exists.")

        async with self.uow:
            user = User(
                username=username,
                email=email,
                full_name=full_name,
                avatar_color=avatar_color or "#4F46E5",
                hashed_password=self.hasher.hash(password),
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
