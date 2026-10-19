from datetime import datetime
from typing import Protocol, List, Optional
from app.domain.entities.memory import AgentMemory


class IMemoryRepository(Protocol):
    async def list_user_memories(
        self,
        user_id: str,
        scope: Optional[str] = None,
        agent_id: Optional[str] = None,
        category: Optional[str] = None,
        active_only: bool = True,
    ) -> List[AgentMemory]:
        ...

    async def list_household_memories(
        self,
        category: Optional[str] = None,
    ) -> List[AgentMemory]:
        ...

    async def list_current_personal(self, user_id: str, now: datetime) -> List[AgentMemory]:
        """The member's active, unexpired personal memories, newest first."""
        ...

    async def list_current_household(self, now: datetime) -> List[AgentMemory]:
        """Every active, unexpired household memory, newest first."""
        ...

    async def list_by_event(self, user_id: str, event_id: str) -> List[AgentMemory]:
        """The member's active memories linked to that event of their calendar (memory spec §7)."""
        ...

    async def list_linked_current(self, now: datetime) -> List[AgentMemory]:
        """Every active, unexpired memory linked to an event: what the periodic check reads."""
        ...

    async def get_by_id(self, memory_id: str) -> Optional[AgentMemory]:
        ...

    async def create(self, memory: AgentMemory) -> AgentMemory:
        ...

    async def update(self, memory: AgentMemory) -> AgentMemory:
        ...

    async def delete(self, memory_id: str) -> None:
        ...

    async def delete_personal_memories(self, user_id: str) -> None:
        ...

    async def reassign_household_memories(self, from_user_id: str, to_user_id: str) -> None:
        ...
