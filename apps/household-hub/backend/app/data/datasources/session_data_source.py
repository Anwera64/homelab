from dataclasses import dataclass
from typing import Protocol, List, Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update, delete
from sqlalchemy.orm import joinedload

from app.data.models.session_model import SessionModel, MessageModel


@dataclass(frozen=True)
class SessionListRow:
    """
    A session as the Chats list needs it: the row itself, plus the newest thing said in it.

    The preview is not a column and never will be — it belongs to whichever message is latest, so
    it is read alongside the session rather than stored on it. The agent travels on
    `session.agent`, eagerly loaded, so the mapper can reach its name and avatar without a second
    query per row.
    """

    session: SessionModel
    last_message_preview: Optional[str]


class ISessionDataSource(Protocol):
    async def list_by_user_id(self, user_id: str) -> List[SessionListRow]:
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

    async def list_by_user_id(self, user_id: str) -> List[SessionListRow]:
        # One correlated sub-select per row for the newest message. It is a seek rather than a
        # scan because of ix_chat_messages_session_created; without that index this is the most
        # expensive query in the hub.
        newest_message = (
            select(MessageModel.content)
            .where(MessageModel.session_id == SessionModel.id)
            .order_by(MessageModel.created_at.desc())
            .limit(1)
            .correlate(SessionModel)
            .scalar_subquery()
        )

        stmt = (
            select(SessionModel, newest_message.label("last_message_preview"))
            .options(joinedload(SessionModel.agent))
            .where(SessionModel.user_id == user_id)
            .order_by(SessionModel.updated_at.desc())
        )
        res = await self.session.execute(stmt)
        return [
            SessionListRow(session=session, last_message_preview=preview)
            for session, preview in res.all()
        ]

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
