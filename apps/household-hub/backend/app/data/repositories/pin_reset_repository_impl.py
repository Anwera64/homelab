from datetime import datetime
from typing import Optional

from app.domain.entities.pin_reset import PinReset
from app.domain.repositories.pin_reset_repository import IPinResetRepository
from app.data.datasources.pin_reset_data_source import IPinResetDataSource
from app.data.mappers.pin_reset_data_mapper import PinResetDataMapper


class PinResetRepositoryImpl(IPinResetRepository):
    def __init__(self, data_source: IPinResetDataSource, mapper: PinResetDataMapper):
        self.data_source = data_source
        self.mapper = mapper

    async def create(self, reset: PinReset) -> PinReset:
        model = self.mapper.to_model(reset)
        created = await self.data_source.create(model)
        return self.mapper.to_domain(created)

    async def get_by_code(self, code: str) -> Optional[PinReset]:
        model = await self.data_source.get_by_code(code)
        return self.mapper.to_domain(model) if model else None

    async def delete_unused_for_target(self, target_user_id: str) -> None:
        await self.data_source.delete_unused_for_target(target_user_id)

    async def claim(self, reset_id: str, used_at: datetime) -> bool:
        return await self.data_source.claim(reset_id, used_at)
