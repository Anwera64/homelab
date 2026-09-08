from typing import Optional
from app.domain.entities.user import User
from app.domain.repositories.user_repository import IUserRepository
from app.domain.repositories.space_repository import ISpaceRepository
from app.domain.repositories.agent_repository import IAgentRepository
from app.domain.repositories.memory_repository import IMemoryRepository
from app.domain.repositories.gossip_repository import IGossipRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.exceptions import EntityNotFoundException, SoleAdminDeletionException


class DeleteMemberUseCase:
    def __init__(
        self,
        user_repo: IUserRepository,
        space_repo: ISpaceRepository,
        agent_repo: IAgentRepository,
        memory_repo: IMemoryRepository,
        uow: IUnitOfWork,
        gossip_repo: Optional[IGossipRepository] = None,
    ):
        self.user_repo = user_repo
        self.space_repo = space_repo
        self.agent_repo = agent_repo
        self.memory_repo = memory_repo
        self.uow = uow
        self.gossip_repo = gossip_repo

    async def execute(self, user_id_to_delete: str, current_admin: User) -> None:
        user = await self.user_repo.get_by_id(user_id_to_delete)
        if not user:
            raise EntityNotFoundException("Member not found")

        if user.is_admin:
            admin_count = await self.user_repo.count_admins()
            if admin_count <= 1:
                raise SoleAdminDeletionException(
                    "Cannot delete the only administrator account. Promote another member to admin first."
                )

        # Determine inheriting admin
        if current_admin.id != user.id:
            inheriting_admin = current_admin
        else:
            inheriting_admin = await self.user_repo.get_other_admin(exclude_user_id=user.id)

        async with self.uow:
            if inheriting_admin:
                # Reassign custom agents
                await self.agent_repo.reassign_owner(from_user_id=user.id, to_user_id=inheriting_admin.id)
                # Reassign household memories
                await self.memory_repo.reassign_household_memories(from_user_id=user.id, to_user_id=inheriting_admin.id)
                # Reassign household milestones
                if self.gossip_repo:
                    await self.gossip_repo.reassign_household_milestones(from_user_id=user.id, to_user_id=inheriting_admin.id)

            # Purge personal memories (Strict Zero-Leak)
            await self.memory_repo.delete_personal_memories(user_id=user.id)

            # Purge personal space
            await self.space_repo.delete_by_owner_id(owner_id=user.id)

            # Delete user
            await self.user_repo.delete(user_id=user.id)
            await self.uow.commit()
