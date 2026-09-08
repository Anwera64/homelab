from datetime import datetime, timezone, timedelta
from typing import List, Optional
from app.domain.entities.user import User
from app.domain.entities.agent import AgentPersonality
from app.domain.repositories.agent_repository import IAgentRepository
from app.domain.repositories.session_repository import ISessionRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.exceptions import SlugConflictException, InvalidOperationException


class CreateAgentUseCase:
    def __init__(
        self,
        agent_repo: IAgentRepository,
        session_repo: ISessionRepository,
        uow: IUnitOfWork,
        grace_days: int = 7,
    ):
        self.agent_repo = agent_repo
        self.session_repo = session_repo
        self.uow = uow
        self.grace_days = grace_days

    async def execute(
        self,
        current_user: User,
        slug: str,
        name: str,
        system_prompt: str,
        description: str = "",
        avatar: str = "🤖",
        model_alias: str = "qwen3:14b",
        temperature: float = 0.7,
        top_p: float = 0.9,
        tool_permissions: Optional[List[str]] = None,
    ) -> AgentPersonality:
        async with self.uow:
            # 1. Purge expired trash agents
            cutoff = datetime.now(timezone.utc) - timedelta(days=self.grace_days)
            purged_ids = await self.agent_repo.purge_expired_trash(cutoff)
            for pid in purged_ids:
                await self.session_repo.archive_by_agent_id(pid)

            # 2. Check active slug
            existing_active = await self.agent_repo.get_by_slug(slug, include_deleted=False)
            if existing_active:
                raise InvalidOperationException(f"An active agent with slug '{slug}' already exists.")

            # 3. Check trash slug
            existing_any = await self.agent_repo.get_by_slug(slug, include_deleted=True)
            if existing_any and existing_any.deleted_at is not None:
                raise InvalidOperationException(
                    f"An agent with slug '{slug}' is currently in trash (under {self.grace_days}-day grace period). Restore it or purge trash to reuse this slug."
                )

            agent = AgentPersonality(
                slug=slug,
                name=name,
                description=description or "",
                avatar=avatar or "🤖",
                system_prompt=system_prompt,
                model_alias=model_alias or "qwen3:14b",
                temperature=temperature if temperature is not None else 0.7,
                top_p=top_p if top_p is not None else 0.9,
                tool_permissions=tool_permissions or [],
                owner_id=current_user.id,
                is_builtin=False,
                is_active=True,
            )
            created = await self.agent_repo.create(agent)
            await self.uow.commit()

        return created
