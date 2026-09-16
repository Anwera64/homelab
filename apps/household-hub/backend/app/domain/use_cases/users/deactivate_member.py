from typing import Optional

from app.domain.entities.user import User
from app.domain.exceptions import (
    EntityNotFoundException,
    InvalidOperationException,
    SoleAdminDeletionException,
)
from app.domain.repositories.agent_repository import IAgentRepository
from app.domain.repositories.calendar_credential_repository import ICalendarCredentialRepository
from app.domain.repositories.document_repository import IDocumentRepository
from app.domain.repositories.memory_repository import IMemoryRepository
from app.domain.repositories.session_repository import ISessionRepository
from app.domain.repositories.space_repository import ISpaceRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.repositories.user_repository import IUserRepository


class DeactivateMemberUseCase:
    """
    Removing someone keeps their id, name and colour and switches the account off. Everything private
    to them is erased — chats, personal memories, their space, their calendar connection, their notes
    and their PIN — while what they shared with the household keeps their name on it. Agents they
    made pass to an admin, because an agent needs a living owner to stay editable.

    The token version moves on, so the phone they are holding stops working.
    """

    def __init__(
        self,
        user_repo: IUserRepository,
        space_repo: ISpaceRepository,
        agent_repo: IAgentRepository,
        memory_repo: IMemoryRepository,
        session_repo: ISessionRepository,
        document_repo: IDocumentRepository,
        calendar_repo: ICalendarCredentialRepository,
        uow: IUnitOfWork,
    ):
        self.user_repo = user_repo
        self.space_repo = space_repo
        self.agent_repo = agent_repo
        self.memory_repo = memory_repo
        self.session_repo = session_repo
        self.document_repo = document_repo
        self.calendar_repo = calendar_repo
        self.uow = uow

    async def execute(self, user_id_to_remove: str, current_admin: User) -> None:
        """An admin removes someone else. Leaving the household yourself is its own flow."""
        if user_id_to_remove == current_admin.id:
            raise InvalidOperationException("Leaving the household is its own door: delete your account instead.")

        member = await self.user_repo.get_by_id(user_id_to_remove)
        if not member or not member.is_active:
            raise EntityNotFoundException("Member not found")

        await self.remove(member, inheriting_admin=current_admin)

    async def remove(self, member: User, inheriting_admin: Optional[User]) -> None:
        """Switch the account off and erase what was private to it. Shared by removing and leaving."""
        if member.is_admin and await self.user_repo.count_admins() <= 1:
            raise SoleAdminDeletionException("You're the only admin, so the household can't lose you.")

        async with self.uow:
            if inheriting_admin:
                await self.agent_repo.reassign_owner(from_user_id=member.id, to_user_id=inheriting_admin.id)

            for session in await self.session_repo.list_by_user_id(member.id):
                await self.session_repo.delete(session.id)
            for document in await self.document_repo.list_by_user(member.id):
                await self.document_repo.delete(document.id)
            await self.calendar_repo.delete_by_user_id(member.id)
            await self.memory_repo.delete_personal_memories(user_id=member.id)
            await self.space_repo.delete_by_owner_id(owner_id=member.id)

            member.is_active = False
            member.hashed_pin = ""
            member.personal_space_id = None
            member.failed_pin_attempts = 0
            member.pin_locked_until = None
            member.token_version += 1
            await self.user_repo.update(member)

            await self.uow.commit()
