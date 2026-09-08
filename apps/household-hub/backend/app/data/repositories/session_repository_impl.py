from typing import List, Optional
from app.domain.entities.session import ConversationSession, ChatMessage
from app.domain.repositories.session_repository import ISessionRepository
from app.data.datasources.session_data_source import ISessionDataSource
from app.data.mappers.session_data_mapper import SessionDataMapper


class SessionRepositoryImpl(ISessionRepository):
    def __init__(self, data_source: ISessionDataSource, mapper: SessionDataMapper):
        self.data_source = data_source
        self.mapper = mapper

    async def list_by_user_id(self, user_id: str) -> List[ConversationSession]:
        models = await self.data_source.list_by_user_id(user_id)
        return [self.mapper.to_domain_session(m) for m in models]

    async def get_by_id(self, session_id: str) -> Optional[ConversationSession]:
        model = await self.data_source.get_by_id(session_id)
        return self.mapper.to_domain_session(model) if model else None

    async def create(self, session: ConversationSession) -> ConversationSession:
        model = self.mapper.to_model_session(session)
        created = await self.data_source.create(model)
        return self.mapper.to_domain_session(created)

    async def update(self, session: ConversationSession) -> ConversationSession:
        model = await self.data_source.get_by_id(session.id)
        if model:
            model.title = session.title
            model.is_secret = session.is_secret
            model.is_archived = session.is_archived
            model.agent_id = session.agent_id
            model.updated_at = session.updated_at
            updated = await self.data_source.update(model)
            return self.mapper.to_domain_session(updated)
        else:
            model = self.mapper.to_model_session(session)
            updated = await self.data_source.update(model)
            return self.mapper.to_domain_session(updated)

    async def delete(self, session_id: str) -> None:
        await self.data_source.delete(session_id)

    async def archive_by_agent_id(self, agent_id: str) -> None:
        await self.data_source.archive_by_agent_id(agent_id)

    async def add_message(self, message: ChatMessage) -> ChatMessage:
        model = self.mapper.to_model_message(message)
        created = await self.data_source.add_message(model)
        return self.mapper.to_domain_message(created)

    async def get_messages(self, session_id: str, limit: int = 50, before_id: Optional[str] = None) -> List[ChatMessage]:
        models = await self.data_source.get_messages(session_id, limit, before_id)
        return [self.mapper.to_domain_message(m) for m in models]
