from app.domain.entities.user import User
from app.domain.repositories.session_repository import ISessionRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.exceptions import EntityNotFoundException, ZeroLeakViolationException


class DeleteSessionUseCase:
    def __init__(self, session_repo: ISessionRepository, uow: IUnitOfWork):
        self.session_repo = session_repo
        self.uow = uow

    async def execute(self, session_id: str, current_user: User) -> None:
        session = await self.session_repo.get_by_id(session_id)
        if not session:
            raise EntityNotFoundException("Session not found")

        if session.user_id != current_user.id:
            raise ZeroLeakViolationException("Zero-Leak Privacy violation: You cannot delete another member's session.")

        async with self.uow:
            await self.session_repo.delete(session_id)
            await self.uow.commit()
