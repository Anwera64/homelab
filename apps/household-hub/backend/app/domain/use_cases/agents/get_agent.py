from app.domain.entities.agent import AgentPersonality
from app.domain.repositories.agent_repository import IAgentRepository
from app.domain.exceptions import EntityNotFoundException


class GetAgentUseCase:
    def __init__(self, agent_repo: IAgentRepository):
        self.agent_repo = agent_repo

    async def execute(self, id_or_slug: str) -> AgentPersonality:
        agent = await self.agent_repo.get_by_id_or_slug(id_or_slug)
        if not agent or agent.deleted_at is not None:
            raise EntityNotFoundException("Agent personality not found")
        return agent
