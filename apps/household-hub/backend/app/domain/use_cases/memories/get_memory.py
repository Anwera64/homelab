from app.domain.entities.user import User
from app.domain.entities.memory import AgentMemory
from app.domain.repositories.memory_repository import IMemoryRepository
from app.domain.exceptions import EntityNotFoundException, ZeroLeakViolationException


class GetMemoryUseCase:
    def __init__(self, memory_repo: IMemoryRepository):
        self.memory_repo = memory_repo

    async def execute(self, memory_id: str, current_user: User) -> AgentMemory:
        memory = await self.memory_repo.get_by_id(memory_id)
        if not memory:
            raise EntityNotFoundException("Memory not found")

        if memory.scope == "personal" and memory.user_id != current_user.id:
            raise ZeroLeakViolationException(
                "Zero-Leak Privacy violation: You cannot access another member's personal memory."
            )

        return memory
