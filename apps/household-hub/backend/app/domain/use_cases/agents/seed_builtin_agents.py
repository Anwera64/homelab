from typing import List, Dict, Any
from app.domain.entities.agent import AgentPersonality
from app.domain.repositories.agent_repository import IAgentRepository
from app.domain.repositories.unit_of_work import IUnitOfWork

BUILTIN_AGENTS: List[Dict[str, Any]] = [
    {
        "slug": "researcher",
        "name": "Academic & Document Researcher",
        "description": "Specialized agent for academic literature, PDF analysis, architectural research, and long-form document synthesis.",
        "avatar": "📚",
        "system_prompt": (
            "You are the Household Academic & Document Researcher. You specialize in deep academic analysis, "
            "synthesizing complex documents, reading architectural papers, and extracting exact citations. "
            "You maintain an objective, thorough, and rigorous research tone."
        ),
        # A thinking model reasons and calls tools worse when run much colder than it was tuned for.
        "temperature": 0.6,
        "top_p": 0.85,
        "tool_permissions": ["pdf_reader", "searxng_search", "document_writer"],
        "is_builtin": True,
    },
    {
        "slug": "assistant",
        "name": "Home & Life Coordinator",
        "description": "Everyday coordinator managing household calendars, dinner suggestions, shared notices, and personal schedules.",
        "avatar": "🏡",
        "system_prompt": (
            "You are the Home & Life Coordinator for the household. You help members organize their schedules, "
            "coordinate dinners, manage grocery lists, and stay aligned. You maintain a warm, proactive, "
            "and helpful household companion tone."
        ),
        "temperature": 0.7,
        "top_p": 0.9,
        "tool_permissions": ["calendar_read", "calendar_write", "searxng_search"],
        "is_builtin": True,
    },
]


class SeedBuiltinAgentsUseCase:
    def __init__(self, agent_repo: IAgentRepository, uow: IUnitOfWork):
        self.agent_repo = agent_repo
        self.uow = uow

    async def execute(self) -> None:
        async with self.uow:
            for spec in BUILTIN_AGENTS:
                existing = await self.agent_repo.get_by_slug(spec["slug"], include_deleted=True)
                if not existing:
                    agent = AgentPersonality(
                        slug=spec["slug"],
                        name=spec["name"],
                        description=spec["description"],
                        avatar=spec["avatar"],
                        system_prompt=spec["system_prompt"],
                        temperature=spec["temperature"],
                        top_p=spec["top_p"],
                        tool_permissions=spec["tool_permissions"],
                        owner_id=None,
                        is_builtin=True,
                        is_active=True,
                    )
                    await self.agent_repo.create(agent)
            await self.uow.commit()
