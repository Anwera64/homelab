from app.domain.entities.user import User
from app.domain.repositories.memory_repository import IMemoryRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.exceptions import EntityNotFoundException, ZeroLeakViolationException


class DeleteMemoryUseCase:
    def __init__(self, memory_repo: IMemoryRepository, uow: IUnitOfWork):
        self.memory_repo = memory_repo
        self.uow = uow

    async def execute(self, memory_id: str, current_user: User) -> None:
        memory = await self.memory_repo.get_by_id(memory_id)
        if not memory:
            raise EntityNotFoundException("Memory not found")

        if memory.scope == "personal":
            if memory.user_id != current_user.id:
                raise ZeroLeakViolationException("Zero-Leak Privacy violation: Personal memories are strictly private.")
        else:
            if memory.user_id != current_user.id and not current_user.is_admin:
                raise ZeroLeakViolationException("Only the author or a Household Admin can delete shared household memories.")

        async with self.uow:
            await self.memory_repo.delete(memory_id)
            await self.uow.commit()
