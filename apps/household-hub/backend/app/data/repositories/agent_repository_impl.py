from typing import List, Optional
from datetime import datetime
from app.domain.entities.agent import AgentPersonality
from app.domain.repositories.agent_repository import IAgentRepository
from app.data.datasources.agent_data_source import IAgentDataSource
from app.data.mappers.agent_data_mapper import AgentDataMapper


class AgentRepositoryImpl(IAgentRepository):
    def __init__(self, data_source: IAgentDataSource, mapper: AgentDataMapper):
        self.data_source = data_source
        self.mapper = mapper

    async def list_active(self) -> List[AgentPersonality]:
        models = await self.data_source.list_active()
        return [self.mapper.to_domain(m) for m in models]

    async def list_trash(self, owner_id: Optional[str] = None, is_admin: bool = False) -> List[AgentPersonality]:
        models = await self.data_source.list_trash(owner_id=owner_id, is_admin=is_admin)
        return [self.mapper.to_domain(m) for m in models]

    async def get_by_id(self, agent_id: str) -> Optional[AgentPersonality]:
        model = await self.data_source.get_by_id(agent_id)
        return self.mapper.to_domain(model) if model else None

    async def get_by_slug(self, slug: str, include_deleted: bool = False) -> Optional[AgentPersonality]:
        model = await self.data_source.get_by_slug(slug, include_deleted=include_deleted)
        return self.mapper.to_domain(model) if model else None

    async def get_by_id_or_slug(self, id_or_slug: str) -> Optional[AgentPersonality]:
        model = await self.data_source.get_by_id_or_slug(id_or_slug)
        return self.mapper.to_domain(model) if model else None

    async def create(self, agent: AgentPersonality) -> AgentPersonality:
        model = self.mapper.to_model(agent)
        created = await self.data_source.create(model)
        return self.mapper.to_domain(created)

    async def update(self, agent: AgentPersonality) -> AgentPersonality:
        model = await self.data_source.get_by_id(agent.id)
        if model:
            model.slug = agent.slug
            model.name = agent.name
            model.description = agent.description
            model.avatar = agent.avatar
            model.system_prompt = agent.system_prompt
            model.model_alias = agent.model_alias
            model.temperature = agent.temperature
            model.top_p = agent.top_p
            model.tool_permissions = agent.tool_permissions
            model.owner_id = agent.owner_id
            model.is_builtin = agent.is_builtin
            model.is_active = agent.is_active
            model.deleted_at = agent.deleted_at
            updated = await self.data_source.update(model)
            return self.mapper.to_domain(updated)
        else:
            model = self.mapper.to_model(agent)
            updated = await self.data_source.update(model)
            return self.mapper.to_domain(updated)

    async def delete_permanent(self, agent_id: str) -> None:
        await self.data_source.delete_permanent(agent_id)

    async def purge_expired_trash(self, cutoff: datetime) -> List[str]:
        return await self.data_source.purge_expired_trash(cutoff)

    async def reassign_owner(self, from_user_id: str, to_user_id: str) -> None:
        await self.data_source.reassign_owner(from_user_id, to_user_id)
