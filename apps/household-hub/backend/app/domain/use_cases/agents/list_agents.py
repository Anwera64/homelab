from typing import List
from app.domain.entities.agent import AgentPersonality
from app.domain.repositories.agent_repository import IAgentRepository


class ListAgentsUseCase:
    def __init__(self, agent_repo: IAgentRepository):
        self.agent_repo = agent_repo

    async def execute(self) -> List[AgentPersonality]:
        return await self.agent_repo.list_active()
