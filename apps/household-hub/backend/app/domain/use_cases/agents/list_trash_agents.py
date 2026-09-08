from datetime import datetime, timezone
from typing import List, Dict, Any
from app.domain.entities.user import User
from app.domain.repositories.agent_repository import IAgentRepository


class ListTrashAgentsUseCase:
    def __init__(self, agent_repo: IAgentRepository, grace_days: int = 7):
        self.agent_repo = agent_repo
        self.grace_days = grace_days

    async def execute(self, current_user: User) -> List[Dict[str, Any]]:
        agents = await self.agent_repo.list_trash(
            owner_id=current_user.id if not current_user.is_admin else None,
            is_admin=current_user.is_admin,
        )
        now = datetime.now(timezone.utc)
        items = []
        for a in agents:
            if a.deleted_at:
                days_rem = a.days_remaining_in_grace_period(now=now, grace_days=self.grace_days)
                # Only include models whose grace period has not expired
                del_at = a.deleted_at if a.deleted_at.tzinfo else a.deleted_at.replace(tzinfo=timezone.utc)
                if (now - del_at).days <= self.grace_days:
                    items.append({
                        "agent": a,
                        "days_remaining_in_grace_period": days_rem,
                    })
        return items
