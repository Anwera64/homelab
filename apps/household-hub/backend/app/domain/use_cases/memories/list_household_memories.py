from typing import List, Optional
from app.domain.entities.memory import AgentMemory
from app.domain.repositories.memory_repository import IMemoryRepository


class ListHouseholdMemoriesUseCase:
    def __init__(self, memory_repo: IMemoryRepository):
        self.memory_repo = memory_repo

    async def execute(self, category: Optional[str] = None) -> List[AgentMemory]:
        return await self.memory_repo.list_household_memories(category=category)
