from app.domain.entities.user import User
from app.domain.entities.session import ConversationSession
from app.domain.repositories.session_repository import ISessionRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.exceptions import EntityNotFoundException, ZeroLeakViolationException


class ToggleSecretModeUseCase:
    def __init__(self, session_repo: ISessionRepository, uow: IUnitOfWork):
        self.session_repo = session_repo
        self.uow = uow

    async def execute(self, session_id: str, is_secret: bool, current_user: User) -> ConversationSession:
        session = await self.session_repo.get_by_id(session_id)
        if not session:
            raise EntityNotFoundException("Session not found")

        if session.user_id != current_user.id:
            raise ZeroLeakViolationException("Zero-Leak Privacy violation: You cannot modify another member's session.")

        async with self.uow:
            session.is_secret = is_secret
            updated = await self.session_repo.update(session)
            await self.uow.commit()

        return updated
