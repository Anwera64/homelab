from typing import List, Optional
from app.domain.entities.memory import AgentMemory
from app.domain.repositories.memory_repository import IMemoryRepository


class ListUserMemoriesUseCase:
    def __init__(self, memory_repo: IMemoryRepository):
        self.memory_repo = memory_repo

    async def execute(
        self,
        user_id: str,
        scope: Optional[str] = None,
        agent_id: Optional[str] = None,
        category: Optional[str] = None,
        active_only: bool = True,
    ) -> List[AgentMemory]:
        return await self.memory_repo.list_user_memories(
            user_id=user_id,
            scope=scope,
            agent_id=agent_id,
            category=category,
            active_only=active_only,
        )
