from typing import List, Optional
from app.domain.entities.user import User
from app.domain.repositories.user_repository import IUserRepository
from app.data.datasources.user_data_source import IUserDataSource
from app.data.mappers.user_data_mapper import UserDataMapper


class UserRepositoryImpl(IUserRepository):
    def __init__(self, data_source: IUserDataSource, mapper: UserDataMapper):
        self.data_source = data_source
        self.mapper = mapper

    async def count(self) -> int:
        return await self.data_source.count()

    async def get_by_id(self, user_id: str) -> Optional[User]:
        model = await self.data_source.get_by_id(user_id)
        return self.mapper.to_domain(model) if model else None

    async def list_all(self) -> List[User]:
        models = await self.data_source.list_all()
        return [self.mapper.to_domain(m) for m in models]

    async def list_active(self) -> List[User]:
        models = await self.data_source.list_active()
        return [self.mapper.to_domain(m) for m in models]

    async def create(self, user: User) -> User:
        model = self.mapper.to_model(user)
        created = await self.data_source.create(model)
        return self.mapper.to_domain(created)

    async def update(self, user: User) -> User:
        model = await self.data_source.get_by_id(user.id)
        if model:
            model.full_name = user.full_name
            model.hashed_pin = user.hashed_pin
            model.avatar_color = user.avatar_color
            model.is_admin = user.is_admin
            model.is_active = user.is_active
            model.failed_pin_attempts = user.failed_pin_attempts
            model.pin_locked_until = user.pin_locked_until
            updated = await self.data_source.update(model)
            return self.mapper.to_domain(updated)
        else:
            model = self.mapper.to_model(user)
            updated = await self.data_source.update(model)
            return self.mapper.to_domain(updated)

    async def delete(self, user_id: str) -> None:
        await self.data_source.delete(user_id)

    async def count_admins(self) -> int:
        return await self.data_source.count_admins()

    async def get_other_admin(self, exclude_user_id: str) -> Optional[User]:
        model = await self.data_source.get_other_admin(exclude_user_id)
        return self.mapper.to_domain(model) if model else None
