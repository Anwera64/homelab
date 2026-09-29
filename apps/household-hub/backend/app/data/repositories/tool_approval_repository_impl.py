from typing import List

from app.domain.entities.tool_approval import ToolApproval
from app.domain.repositories.tool_approval_repository import IToolApprovalRepository
from app.data.datasources.tool_approval_data_source import IToolApprovalDataSource
from app.data.mappers.tool_approval_data_mapper import ToolApprovalDataMapper


class ToolApprovalRepositoryImpl(IToolApprovalRepository):
    def __init__(self, data_source: IToolApprovalDataSource, mapper: ToolApprovalDataMapper):
        self.data_source = data_source
        self.mapper = mapper

    async def list_for_user(self, user_id: str) -> List[ToolApproval]:
        return [self.mapper.to_domain(model) for model in await self.data_source.list_for_user(user_id)]

    async def set(self, user_id: str, tool: str, action: str, auto: bool) -> None:
        await self.data_source.set(user_id, tool, action, auto)
