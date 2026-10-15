import logging
from datetime import datetime, timezone
from typing import Callable

from app.domain.entities.memory import AgentMemory
from app.domain.entities.user import User
from app.domain.exceptions import EntityNotFoundException, ZeroLeakViolationException
from app.domain.repositories.memory_repository import IMemoryRepository
from app.domain.repositories.unit_of_work import IUnitOfWork

logger = logging.getLogger(__name__)


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


class RetireMemoryUseCase:
    """✕ on the memory page (memory spec §3.1). The memory is made inactive, never deleted, so Undo can bring it back.

    Only the member who said it may remove it, whatever its scope: an admin can't remove another member's
    household memory either.
    """

    def __init__(self, memory_repo: IMemoryRepository, uow: IUnitOfWork, clock: Callable[[], datetime] = _utc_now):
        self.memory_repo = memory_repo
        self.uow = uow
        self.clock = clock

    async def execute(self, memory_id: str, current_user: User) -> AgentMemory:
        memory = await self.memory_repo.get_by_id(memory_id)
        if not memory:
            raise EntityNotFoundException("Memory not found")
        if memory.user_id != current_user.id:
            logger.info("Memory remove refused | member=%s memory=%s reason=not_theirs", current_user.id, memory_id)
            raise ZeroLeakViolationException("Only the member who said a memory can remove it.")

        async with self.uow:
            memory.is_active = False
            memory.retired_at = self.clock()
            memory = await self.memory_repo.update(memory)
            await self.uow.commit()
        logger.info("Memory removed | member=%s memory=%s", current_user.id, memory_id)
        return memory
