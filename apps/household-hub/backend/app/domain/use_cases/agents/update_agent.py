from typing import List, Optional
from app.domain.entities.user import User
from app.domain.entities.agent import AgentPersonality
from app.domain.repositories.agent_repository import IAgentRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.exceptions import EntityNotFoundException, ZeroLeakViolationException, InvalidOperationException


class UpdateAgentUseCase:
    def __init__(self, agent_repo: IAgentRepository, uow: IUnitOfWork):
        self.agent_repo = agent_repo
        self.uow = uow

    async def execute(
        self,
        agent_id: str,
        current_user: User,
        slug: Optional[str] = None,
        name: Optional[str] = None,
        description: Optional[str] = None,
        avatar: Optional[str] = None,
        system_prompt: Optional[str] = None,
        llm_model_id: Optional[str] = None,
        temperature: Optional[float] = None,
        top_p: Optional[float] = None,
        tool_permissions: Optional[List[str]] = None,
        is_active: Optional[bool] = None,
    ) -> AgentPersonality:
        agent = await self.agent_repo.get_by_id(agent_id)
        if not agent or agent.deleted_at is not None:
            raise EntityNotFoundException("Agent personality not found")

        if agent.is_builtin:
            if not current_user.is_admin:
                raise ZeroLeakViolationException("Only the Household Admin can modify built-in system agents.")
        else:
            if agent.owner_id != current_user.id:
                raise ZeroLeakViolationException("Only the model owner can edit this model.")

        async with self.uow:
            if slug is not None and slug != agent.slug:
                existing = await self.agent_repo.get_by_slug(slug, include_deleted=True)
                if existing and existing.id != agent.id:
                    raise InvalidOperationException(f"Agent with slug '{slug}' already exists or is in trash.")
                agent.slug = slug

            if name is not None:
                agent.name = name
            if description is not None:
                agent.description = description
            if avatar is not None:
                agent.avatar = avatar
            if system_prompt is not None:
                agent.system_prompt = system_prompt
            if llm_model_id is not None:
                agent.llm_model_id = llm_model_id
            if temperature is not None:
                agent.temperature = temperature
            if top_p is not None:
                agent.top_p = top_p
            if tool_permissions is not None:
                agent.tool_permissions = tool_permissions
            if is_active is not None:
                agent.is_active = is_active

            updated = await self.agent_repo.update(agent)
            await self.uow.commit()

        return updated
