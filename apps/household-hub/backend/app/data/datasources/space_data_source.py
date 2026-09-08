from typing import Protocol, Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, delete

from app.data.models.space_model import SpaceModel


class ISpaceDataSource(Protocol):
    async def get_shared(self) -> Optional[SpaceModel]:
        ...

    async def get_by_id(self, space_id: str) -> Optional[SpaceModel]:
        ...

    async def get_by_owner_id(self, owner_id: str) -> Optional[SpaceModel]:
        ...

    async def create(self, space: SpaceModel) -> SpaceModel:
        ...

    async def update(self, space: SpaceModel) -> SpaceModel:
        ...

    async def delete_by_owner_id(self, owner_id: str) -> None:
        ...


class SqliteSpaceDataSource(ISpaceDataSource):
    def __init__(self, session: AsyncSession):
        self.session = session

    async def get_shared(self) -> Optional[SpaceModel]:
        res = await self.session.execute(select(SpaceModel).where(SpaceModel.type == "shared"))
        return res.scalars().first()

    async def get_by_id(self, space_id: str) -> Optional[SpaceModel]:
        res = await self.session.execute(select(SpaceModel).where(SpaceModel.id == space_id))
        return res.scalars().first()

    async def get_by_owner_id(self, owner_id: str) -> Optional[SpaceModel]:
        res = await self.session.execute(select(SpaceModel).where(SpaceModel.owner_id == owner_id))
        return res.scalars().first()

    async def create(self, space: SpaceModel) -> SpaceModel:
        self.session.add(space)
        await self.session.flush()
        return space

    async def update(self, space: SpaceModel) -> SpaceModel:
        self.session.add(space)
        await self.session.flush()
        return space

    async def delete_by_owner_id(self, owner_id: str) -> None:
        await self.session.execute(delete(SpaceModel).where(SpaceModel.owner_id == owner_id))
        await self.session.flush()
