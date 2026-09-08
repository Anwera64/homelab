from typing import Optional
from app.domain.entities.space import Space
from app.domain.repositories.space_repository import ISpaceRepository
from app.data.datasources.space_data_source import ISpaceDataSource
from app.data.mappers.space_data_mapper import SpaceDataMapper


class SpaceRepositoryImpl(ISpaceRepository):
    def __init__(self, data_source: ISpaceDataSource, mapper: SpaceDataMapper):
        self.data_source = data_source
        self.mapper = mapper

    async def get_shared(self) -> Optional[Space]:
        model = await self.data_source.get_shared()
        return self.mapper.to_domain(model) if model else None

    async def get_by_id(self, space_id: str) -> Optional[Space]:
        model = await self.data_source.get_by_id(space_id)
        return self.mapper.to_domain(model) if model else None

    async def get_by_owner_id(self, owner_id: str) -> Optional[Space]:
        model = await self.data_source.get_by_owner_id(owner_id)
        return self.mapper.to_domain(model) if model else None

    async def create(self, space: Space) -> Space:
        model = self.mapper.to_model(space)
        created = await self.data_source.create(model)
        return self.mapper.to_domain(created)

    async def update(self, space: Space) -> Space:
        model = await self.data_source.get_by_id(space.id)
        if model:
            model.name = space.name
            model.settings = space.settings
            updated = await self.data_source.update(model)
            return self.mapper.to_domain(updated)
        else:
            model = self.mapper.to_model(space)
            updated = await self.data_source.update(model)
            return self.mapper.to_domain(updated)

    async def delete_by_owner_id(self, owner_id: str) -> None:
        await self.data_source.delete_by_owner_id(owner_id)
