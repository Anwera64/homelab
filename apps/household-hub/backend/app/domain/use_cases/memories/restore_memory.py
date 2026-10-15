import logging

from app.domain.entities.memory import AgentMemory
from app.domain.entities.user import User
from app.domain.exceptions import EntityNotFoundException, InvalidOperationException, ZeroLeakViolationException
from app.domain.repositories.memory_repository import IMemoryRepository
from app.domain.repositories.unit_of_work import IUnitOfWork

logger = logging.getLogger(__name__)


class RestoreMemoryUseCase:
    """Undo after ✕ (memory spec §3.1): the member's removed memory is active again.

    A memory retired in favour of another (Use new) is not undone here; that answer has its own history.
    """

    def __init__(self, memory_repo: IMemoryRepository, uow: IUnitOfWork):
        self.memory_repo = memory_repo
        self.uow = uow

    async def execute(self, memory_id: str, current_user: User) -> AgentMemory:
        memory = await self.memory_repo.get_by_id(memory_id)
        if not memory:
            raise EntityNotFoundException("Memory not found")
        if memory.user_id != current_user.id:
            logger.info("Memory restore refused | member=%s memory=%s reason=not_theirs", current_user.id, memory_id)
            raise ZeroLeakViolationException("Only the member who said a memory can bring it back.")
        if memory.replaced_by_id is not None:
            logger.info("Memory restore refused | member=%s memory=%s reason=replaced", current_user.id, memory_id)
            raise InvalidOperationException("This memory was replaced by a newer one.")
        if memory.is_active:
            return memory

        async with self.uow:
            memory.is_active = True
            memory.retired_at = None
            memory = await self.memory_repo.update(memory)
            await self.uow.commit()
        logger.info("Memory restored | member=%s memory=%s", current_user.id, memory_id)
        return memory
