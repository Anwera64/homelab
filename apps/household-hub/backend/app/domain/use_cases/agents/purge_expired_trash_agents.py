from datetime import datetime, timezone, timedelta
from typing import List
from app.domain.repositories.agent_repository import IAgentRepository
from app.domain.repositories.session_repository import ISessionRepository
from app.domain.repositories.unit_of_work import IUnitOfWork


class PurgeExpiredTrashAgentsUseCase:
    """
    Safely purges agents whose trash grace period has expired.
    Ensures that conversation sessions referencing the doomed agents are archived
    BEFORE the agents are deleted, preventing orphaned sessions from having their
    agent_id set to NULL via foreign key cascade before archival can execute.
    """

    def __init__(
        self,
        agent_repo: IAgentRepository,
        session_repo: ISessionRepository,
        uow: IUnitOfWork,
        grace_days: int = 7,
    ):
        self.agent_repo = agent_repo
        self.session_repo = session_repo
        self.uow = uow
        self.grace_days = grace_days

    async def execute(self) -> List[str]:
        cutoff = datetime.now(timezone.utc) - timedelta(days=self.grace_days)
        async with self.uow:
            expired_ids = await self.agent_repo.get_expired_trash_ids(cutoff)
            if not expired_ids:
                return []

            # 1. Archive associated sessions BEFORE agent deletion
            for agent_id in expired_ids:
                await self.session_repo.archive_by_agent_id(agent_id)

            # 2. Delete the expired agents permanently
            await self.agent_repo.purge_expired_trash(cutoff)
            await self.uow.commit()
            return expired_ids
