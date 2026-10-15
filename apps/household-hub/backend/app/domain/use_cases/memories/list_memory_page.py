import logging
from datetime import datetime, timezone
from typing import Callable

from app.domain.entities.memory import AgentMemory, MemoryPage, MemoryPageItem
from app.domain.entities.user import User
from app.domain.repositories.agent_repository import IAgentRepository
from app.domain.repositories.memory_repository import IMemoryRepository
from app.domain.repositories.user_repository import IUserRepository

logger = logging.getLogger(__name__)

# A memory whose agent was purged still says who noted it, just not by name (memory spec §3.2).
UNKNOWN_AGENT_NAME = "an agent"
UNKNOWN_AGENT_AVATAR = "🤖"


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


class ListMemoryPageUseCase:
    """The memory page (memory spec §8): your personal memories, and every household memory with who said it.

    Only current memories show: retired and expired ones are left out, as they are from the prompt.
    """

    def __init__(
        self,
        memory_repo: IMemoryRepository,
        user_repo: IUserRepository,
        agent_repo: IAgentRepository,
        clock: Callable[[], datetime] = _utc_now,
    ):
        self.memory_repo = memory_repo
        self.user_repo = user_repo
        self.agent_repo = agent_repo
        self.clock = clock

    async def execute(self, current_user: User) -> MemoryPage:
        now = self.clock()
        personal = await self.memory_repo.list_current_personal(current_user.id, now=now)
        household = await self.memory_repo.list_current_household(now=now)
        names: dict[str, str] = {current_user.id: current_user.full_name}
        agents: dict[str, tuple[str, str]] = {}
        page = MemoryPage(
            yours=[await self._item(m, current_user, names, agents) for m in personal],
            household=[await self._item(m, current_user, names, agents) for m in household],
        )
        logger.info(
            "Memory page listed | member=%s yours=%d household=%d",
            current_user.id,
            len(page.yours),
            len(page.household),
        )
        return page

    async def _item(
        self,
        memory: AgentMemory,
        current_user: User,
        names: dict[str, str],
        agents: dict[str, tuple[str, str]],
    ) -> MemoryPageItem:
        if memory.user_id not in names:
            speaker = await self.user_repo.get_by_id(memory.user_id)
            names[memory.user_id] = speaker.full_name if speaker else ""
        agent_name, agent_avatar = await self._agent(memory.agent_id, agents)
        is_mine = memory.user_id == current_user.id
        return MemoryPageItem(
            memory=memory,
            speaker_name=names[memory.user_id],
            is_mine=is_mine,
            agent_name=agent_name,
            agent_avatar=agent_avatar,
            can_remove=is_mine,
        )

    async def _agent(self, agent_id: str | None, cache: dict[str, tuple[str, str]]) -> tuple[str, str]:
        if agent_id is None:
            return UNKNOWN_AGENT_NAME, UNKNOWN_AGENT_AVATAR
        if agent_id not in cache:
            agent = await self.agent_repo.get_by_id(agent_id)
            cache[agent_id] = (agent.name, agent.avatar) if agent else (UNKNOWN_AGENT_NAME, UNKNOWN_AGENT_AVATAR)
        return cache[agent_id]
