from typing import Optional
from app.domain.entities.user import User
from app.domain.entities.memory import AgentMemory
from app.domain.repositories.memory_repository import IMemoryRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.exceptions import EntityNotFoundException, ZeroLeakViolationException


class UpdateMemoryUseCase:
    def __init__(self, memory_repo: IMemoryRepository, uow: IUnitOfWork):
        self.memory_repo = memory_repo
        self.uow = uow

    async def execute(
        self,
        memory_id: str,
        current_user: User,
        content: Optional[str] = None,
        confidence: Optional[float] = None,
        is_active: Optional[bool] = None,
    ) -> AgentMemory:
        memory = await self.memory_repo.get_by_id(memory_id)
        if not memory:
            raise EntityNotFoundException("Memory not found")

        # Curation rule:
        # Personal: strictly owner
        # Household: creator or admin
        if memory.scope == "personal":
            if memory.user_id != current_user.id:
                raise ZeroLeakViolationException("Zero-Leak Privacy violation: Personal memories are strictly private.")
        else:
            if memory.user_id != current_user.id and not current_user.is_admin:
                raise ZeroLeakViolationException("Only the author or a Household Admin can modify shared household memories.")

        async with self.uow:
            if content is not None:
                memory.content = content
            if confidence is not None:
                memory.confidence = confidence
            if is_active is not None:
                memory.is_active = is_active

            updated = await self.memory_repo.update(memory)
            await self.uow.commit()

        return updated
