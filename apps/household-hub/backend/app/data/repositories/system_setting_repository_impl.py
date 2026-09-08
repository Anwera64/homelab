from typing import Optional
from app.domain.entities.system_setting import SystemSetting
from app.domain.repositories.system_setting_repository import ISystemSettingRepository
from app.data.datasources.system_setting_data_source import ISystemSettingDataSource
from app.data.mappers.system_setting_data_mapper import SystemSettingDataMapper


class SystemSettingRepositoryImpl(ISystemSettingRepository):
    def __init__(self, data_source: ISystemSettingDataSource, mapper: SystemSettingDataMapper):
        self.data_source = data_source
        self.mapper = mapper

    async def get(self, key: str) -> Optional[SystemSetting]:
        model = await self.data_source.get(key)
        return self.mapper.to_domain(model) if model else None

    async def set(self, key: str, value: str) -> SystemSetting:
        model = await self.data_source.set(key, value)
        return self.mapper.to_domain(model)

    async def set_if_not_exists(self, key: str, value: str) -> bool:
        return await self.data_source.set_if_not_exists(key, value)

    async def delete(self, key: str) -> bool:
        return await self.data_source.delete(key)
