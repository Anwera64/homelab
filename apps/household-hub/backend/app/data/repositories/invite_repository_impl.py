from datetime import datetime
from typing import Optional

from app.domain.entities.invite import Invite
from app.domain.repositories.invite_repository import IInviteRepository
from app.data.datasources.invite_data_source import IInviteDataSource
from app.data.mappers.invite_data_mapper import InviteDataMapper


class InviteRepositoryImpl(IInviteRepository):
    def __init__(self, data_source: IInviteDataSource, mapper: InviteDataMapper):
        self.data_source = data_source
        self.mapper = mapper

    async def create(self, invite: Invite) -> Invite:
        model = self.mapper.to_model(invite)
        created = await self.data_source.create(model)
        return self.mapper.to_domain(created)

    async def get_by_code(self, code: str) -> Optional[Invite]:
        model = await self.data_source.get_by_code(code)
        return self.mapper.to_domain(model) if model else None

    async def delete_unused_for_name(self, invited_name: str) -> None:
        await self.data_source.delete_unused_for_name(invited_name)

    async def claim(self, invite_id: str, used_at: datetime) -> bool:
        return await self.data_source.claim(invite_id, used_at)
