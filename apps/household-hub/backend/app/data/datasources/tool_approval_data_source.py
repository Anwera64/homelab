from datetime import datetime, timezone
from typing import List, Protocol

from sqlalchemy import select
from sqlalchemy.dialects.sqlite import insert as sqlite_insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.data.models.tool_approval_model import ToolApprovalModel


class IToolApprovalDataSource(Protocol):
    async def list_for_user(self, user_id: str) -> List[ToolApprovalModel]:
        ...

    async def set(self, user_id: str, tool: str, action: str, auto: bool) -> None:
        ...


class SqliteToolApprovalDataSource(IToolApprovalDataSource):
    def __init__(self, session: AsyncSession):
        self.session = session

    async def list_for_user(self, user_id: str) -> List[ToolApprovalModel]:
        stmt = select(ToolApprovalModel).where(ToolApprovalModel.user_id == user_id)
        res = await self.session.execute(stmt)
        return list(res.scalars().all())

    async def set(self, user_id: str, tool: str, action: str, auto: bool) -> None:
        now = datetime.now(timezone.utc)
        stmt = sqlite_insert(ToolApprovalModel).values(
            user_id=user_id, tool=tool, action=action, auto=auto, updated_at=now
        )
        stmt = stmt.on_conflict_do_update(
            index_elements=["user_id", "tool", "action"],
            set_={"auto": auto, "updated_at": now},
        )
        await self.session.execute(stmt)
        await self.session.flush()
