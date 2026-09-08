from typing import Protocol, List, Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func
from sqlalchemy.orm import selectinload

from app.data.models.user_model import UserModel


class IUserDataSource(Protocol):
    async def count(self) -> int:
        ...

    async def get_by_id(self, user_id: str) -> Optional[UserModel]:
        ...

    async def get_by_username(self, username: str) -> Optional[UserModel]:
        ...

    async def get_by_username_or_email(self, username: str, email: str) -> Optional[UserModel]:
        ...

    async def list_all(self) -> List[UserModel]:
        ...

    async def create(self, user: UserModel) -> UserModel:
        ...

    async def update(self, user: UserModel) -> UserModel:
        ...

    async def delete(self, user_id: str) -> None:
        ...

    async def count_admins(self) -> int:
        ...

    async def get_other_admin(self, exclude_user_id: str) -> Optional[UserModel]:
        ...


class SqliteUserDataSource(IUserDataSource):
    def __init__(self, session: AsyncSession):
        self.session = session

    async def count(self) -> int:
        res = await self.session.execute(select(func.count(UserModel.id)))
        return res.scalar() or 0

    async def get_by_id(self, user_id: str) -> Optional[UserModel]:
        stmt = select(UserModel).where(UserModel.id == user_id).options(selectinload(UserModel.personal_space))
        res = await self.session.execute(stmt)
        return res.scalars().first()

    async def get_by_username(self, username: str) -> Optional[UserModel]:
        stmt = select(UserModel).where(UserModel.username == username).options(selectinload(UserModel.personal_space))
        res = await self.session.execute(stmt)
        return res.scalars().first()

    async def get_by_username_or_email(self, username: str, email: str) -> Optional[UserModel]:
        stmt = select(UserModel).where((UserModel.username == username) | (UserModel.email == email))
        res = await self.session.execute(stmt)
        return res.scalars().first()

    async def list_all(self) -> List[UserModel]:
        stmt = select(UserModel).options(selectinload(UserModel.personal_space)).order_by(UserModel.created_at)
        res = await self.session.execute(stmt)
        return list(res.scalars().all())

    async def create(self, user: UserModel) -> UserModel:
        self.session.add(user)
        await self.session.flush()
        return user

    async def update(self, user: UserModel) -> UserModel:
        self.session.add(user)
        await self.session.flush()
        return user

    async def delete(self, user_id: str) -> None:
        model = await self.get_by_id(user_id)
        if model:
            await self.session.delete(model)
            await self.session.flush()

    async def count_admins(self) -> int:
        res = await self.session.execute(select(func.count(UserModel.id)).where(UserModel.is_admin.is_(True)))
        return res.scalar() or 0

    async def get_other_admin(self, exclude_user_id: str) -> Optional[UserModel]:
        stmt = select(UserModel).where(UserModel.is_admin.is_(True), UserModel.id != exclude_user_id).limit(1)
        res = await self.session.execute(stmt)
        return res.scalars().first()
