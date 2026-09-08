from typing import Protocol, List, Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update, delete

from app.data.models.session_model import SessionModel, MessageModel


class ISessionDataSource(Protocol):
    async def list_by_user_id(self, user_id: str) -> List[SessionModel]:
        ...

    async def get_by_id(self, session_id: str) -> Optional[SessionModel]:
        ...

    async def create(self, session: SessionModel) -> SessionModel:
        ...

    async def update(self, session: SessionModel) -> SessionModel:
        ...

    async def delete(self, session_id: str) -> None:
        ...

    async def archive_by_agent_id(self, agent_id: str) -> None:
        ...

    async def add_message(self, message: MessageModel) -> MessageModel:
        ...

    async def get_messages(self, session_id: str, limit: int = 50, before_id: Optional[str] = None) -> List[MessageModel]:
        ...


class SqliteSessionDataSource(ISessionDataSource):
    def __init__(self, session: AsyncSession):
        self.session = session

    async def list_by_user_id(self, user_id: str) -> List[SessionModel]:
        stmt = (
            select(SessionModel)
            .where(SessionModel.user_id == user_id)
            .order_by(SessionModel.updated_at.desc())
        )
        res = await self.session.execute(stmt)
        return list(res.scalars().all())

    async def get_by_id(self, session_id: str) -> Optional[SessionModel]:
        res = await self.session.execute(select(SessionModel).where(SessionModel.id == session_id))
        return res.scalars().first()

    async def create(self, session: SessionModel) -> SessionModel:
        self.session.add(session)
        await self.session.flush()
        return session

    async def update(self, session: SessionModel) -> SessionModel:
        self.session.add(session)
        await self.session.flush()
        return session

    async def delete(self, session_id: str) -> None:
        model = await self.get_by_id(session_id)
        if model:
            await self.session.delete(model)
            await self.session.flush()

    async def archive_by_agent_id(self, agent_id: str) -> None:
        await self.session.execute(
            update(SessionModel)
            .where(SessionModel.agent_id == agent_id)
            .values(agent_id=None, is_archived=True)
        )
        await self.session.flush()

    async def add_message(self, message: MessageModel) -> MessageModel:
        self.session.add(message)
        await self.session.flush()
        return message

    async def get_messages(self, session_id: str, limit: int = 50, before_id: Optional[str] = None) -> List[MessageModel]:
        msg_stmt = select(MessageModel).where(MessageModel.session_id == session_id)
        if before_id:
            cursor_res = await self.session.execute(
                select(MessageModel.created_at).where(
                    (MessageModel.id == before_id) & (MessageModel.session_id == session_id)
                )
            )
            cursor_created_at = cursor_res.scalar()
            if cursor_created_at:
                msg_stmt = msg_stmt.where(MessageModel.created_at < cursor_created_at)

        msg_stmt = msg_stmt.order_by(MessageModel.created_at.desc()).limit(limit)
        msg_res = await self.session.execute(msg_stmt)
        return list(reversed(msg_res.scalars().all()))
