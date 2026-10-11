from datetime import datetime
from typing import Optional

from app.domain.entities.device_session import DeviceSession
from app.domain.repositories.device_session_repository import IDeviceSessionRepository
from app.data.datasources.device_session_data_source import IDeviceSessionDataSource
from app.data.mappers.device_session_data_mapper import DeviceSessionDataMapper


class DeviceSessionRepositoryImpl(IDeviceSessionRepository):
    def __init__(self, data_source: IDeviceSessionDataSource, mapper: DeviceSessionDataMapper):
        self.data_source = data_source
        self.mapper = mapper

    async def create(self, device_session: DeviceSession) -> DeviceSession:
        model = self.mapper.to_model(device_session)
        created = await self.data_source.create(model)
        return self.mapper.to_domain(created)

    async def get_by_id(self, device_session_id: str) -> Optional[DeviceSession]:
        model = await self.data_source.get_by_id(device_session_id)
        return self.mapper.to_domain(model) if model else None

    async def touch(self, device_session_id: str, seen_at: datetime) -> None:
        await self.data_source.touch(device_session_id, seen_at)

    async def delete(self, device_session_id: str) -> None:
        await self.data_source.delete(device_session_id)

    async def delete_for_user(self, user_id: str, except_id: Optional[str] = None) -> None:
        await self.data_source.delete_for_user(user_id, except_id)

    async def delete_last_seen_before(self, cutoff: datetime) -> int:
        return await self.data_source.delete_last_seen_before(cutoff)
