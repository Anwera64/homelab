import logging
from datetime import datetime, timezone
from typing import Callable, Optional

from app.domain.entities.memory import AgentMemory, MemoryPage, MemoryPageItem
from app.domain.entities.user import User
from app.domain.repositories.agent_repository import IAgentRepository
from app.domain.repositories.memory_proposal_repository import IMemoryProposalRepository
from app.domain.repositories.memory_repository import IMemoryRepository
from app.domain.repositories.user_repository import IUserRepository

logger = logging.getLogger(__name__)

# A memory whose agent was purged still says who noted it, just not by name (memory spec §3.2).
UNKNOWN_AGENT_NAME = "an agent"
UNKNOWN_AGENT_AVATAR = "🤖"


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


class MemoryItems:
    """Turns memories into page items for one viewer: who said each, which agent noted it, and whether ✕ shows.

    Names are looked up once per speaker and agent.
    """

    def __init__(self, user_repo: IUserRepository, agent_repo: IAgentRepository, viewer: User):
        self.user_repo = user_repo
        self.agent_repo = agent_repo
        self.viewer = viewer
        self.names: dict[str, str] = {viewer.id: viewer.full_name}
        self.agents: dict[str, tuple[str, str]] = {}

    async def name(self, user_id: str | None) -> str:
        if not user_id:
            return ""
        if user_id not in self.names:
            user = await self.user_repo.get_by_id(user_id)
            self.names[user_id] = user.full_name if user else ""
        return self.names[user_id]

    async def item(self, memory: AgentMemory) -> MemoryPageItem:
        agent_name, agent_avatar = await self._agent(memory.agent_id)
        is_mine = memory.user_id == self.viewer.id
        return MemoryPageItem(
            memory=memory,
            speaker_name=await self.name(memory.user_id),
            is_mine=is_mine,
            agent_name=agent_name,
            agent_avatar=agent_avatar,
            can_remove=is_mine,
        )

    async def _agent(self, agent_id: str | None) -> tuple[str, str]:
        if agent_id is None:
            return UNKNOWN_AGENT_NAME, UNKNOWN_AGENT_AVATAR
        if agent_id not in self.agents:
            agent = await self.agent_repo.get_by_id(agent_id)
            self.agents[agent_id] = (agent.name, agent.avatar) if agent else (UNKNOWN_AGENT_NAME, UNKNOWN_AGENT_AVATAR)
        return self.agents[agent_id]


class ListMemoryPageUseCase:
    """The memory page (memory spec §8): your personal memories, and every household memory with who said it.

    Only current memories show: retired and expired ones are left out, as they are from the prompt. The
    page also counts the member's changes to check, for its band and Profile's chip.
    """

    def __init__(
        self,
        memory_repo: IMemoryRepository,
        user_repo: IUserRepository,
        agent_repo: IAgentRepository,
        clock: Callable[[], datetime] = _utc_now,
        proposal_repo: Optional[IMemoryProposalRepository] = None,
    ):
        self.memory_repo = memory_repo
        self.user_repo = user_repo
        self.agent_repo = agent_repo
        self.clock = clock
        self.proposal_repo = proposal_repo

    async def execute(self, current_user: User) -> MemoryPage:
        now = self.clock()
        personal = await self.memory_repo.list_current_personal(current_user.id, now=now)
        household = await self.memory_repo.list_current_household(now=now)
        items = MemoryItems(self.user_repo, self.agent_repo, current_user)
        to_check = (
            len(await self.proposal_repo.list_pending_for_answerer(current_user.id)) if self.proposal_repo else 0
        )
        page = MemoryPage(
            yours=[await items.item(m) for m in personal],
            household=[await items.item(m) for m in household],
            to_check=to_check,
        )
        logger.info(
            "Memory page listed | member=%s yours=%d household=%d to_check=%d",
            current_user.id,
            len(page.yours),
            len(page.household),
            page.to_check,
        )
        return page
