from typing import Protocol, List, Optional
from datetime import datetime
from app.domain.entities.agent import AgentPersonality


class IAgentRepository(Protocol):
    async def list_active(self) -> List[AgentPersonality]:
        ...

    async def list_trash(self, owner_id: Optional[str] = None, is_admin: bool = False) -> List[AgentPersonality]:
        ...

    async def get_by_id(self, agent_id: str) -> Optional[AgentPersonality]:
        ...

    async def get_by_slug(self, slug: str, include_deleted: bool = False) -> Optional[AgentPersonality]:
        ...

    async def get_by_id_or_slug(self, id_or_slug: str) -> Optional[AgentPersonality]:
        ...

    async def create(self, agent: AgentPersonality) -> AgentPersonality:
        ...

    async def update(self, agent: AgentPersonality) -> AgentPersonality:
        ...

    async def delete_permanent(self, agent_id: str) -> None:
        ...

    async def get_expired_trash_ids(self, cutoff: datetime) -> List[str]:
        ...

    async def purge_expired_trash(self, cutoff: datetime) -> List[str]:
        ...

    async def reassign_owner(self, from_user_id: str, to_user_id: str) -> None:
        ...
