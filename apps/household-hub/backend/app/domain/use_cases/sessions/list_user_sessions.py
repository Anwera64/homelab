from typing import List
from app.domain.entities.user import User
from app.domain.entities.session import ConversationSession
from app.domain.repositories.session_repository import ISessionRepository


class ListUserSessionsUseCase:
    def __init__(self, session_repo: ISessionRepository):
        self.session_repo = session_repo

    async def execute(self, current_user: User) -> List[ConversationSession]:
        return await self.session_repo.list_by_user_id(current_user.id)
