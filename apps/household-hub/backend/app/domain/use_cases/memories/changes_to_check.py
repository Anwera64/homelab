import logging
from datetime import datetime
from typing import Dict, List, Optional, Tuple

from app.domain.entities.memory_proposal import (
    BOTH,
    PENDING,
    REPLACED,
    ChangeCard,
    ChangesToCheck,
    LookedOver,
    LookedOverSummary,
    MemoryProposal,
)
from app.domain.entities.memory import MemoryPageItem
from app.domain.entities.session import ConversationSession
from app.domain.entities.user import User
from app.domain.exceptions import EntityNotFoundException
from app.domain.repositories.agent_repository import IAgentRepository
from app.domain.repositories.memory_proposal_repository import IMemoryProposalRepository
from app.domain.repositories.memory_repository import IMemoryRepository
from app.domain.repositories.session_repository import ISessionRepository
from app.domain.repositories.user_repository import IUserRepository
from app.domain.use_cases.memories.list_memory_page import MemoryItems

logger = logging.getLogger(__name__)


class _Cards:
    """Builds change cards for one viewer, looking each chat and name up once."""

    def __init__(
        self,
        memory_repo: IMemoryRepository,
        session_repo: ISessionRepository,
        items: MemoryItems,
        viewer: User,
    ):
        self.memory_repo = memory_repo
        self.session_repo = session_repo
        self.items = items
        self.viewer = viewer
        self.titles: Dict[str, Optional[str]] = {}

    async def card(self, proposal: MemoryProposal) -> Optional[ChangeCard]:
        stored = await self.memory_repo.get_by_id(proposal.memory_id)
        if stored is None:
            return None
        mine = proposal.source_user_id == self.viewer.id
        return ChangeCard(
            proposal=proposal,
            stored=await self.items.item(stored),
            can_answer=proposal.answerer_id == self.viewer.id,
            answerer_name=await self.items.name(proposal.answerer_id),
            source_title=await self._title(proposal.source_session_id) if mine else None,
            source_owner_name=await self.items.name(proposal.source_user_id),
        )

    async def split(self, proposals: List[MemoryProposal]) -> Tuple[List[ChangeCard], List[ChangeCard]]:
        """Pending ones only, newest first: the viewer's to answer, then the ones waiting for others."""
        pending = sorted((p for p in proposals if p.state == PENDING), key=lambda p: p.created_at, reverse=True)
        cards = [c for c in [await self.card(p) for p in pending] if c is not None]
        return [c for c in cards if c.can_answer], [c for c in cards if not c.can_answer]

    async def _title(self, session_id: Optional[str]) -> Optional[str]:
        if not session_id:
            return None
        if session_id not in self.titles:
            chat = await self.session_repo.get_by_id(session_id)
            self.titles[session_id] = chat.title if chat else None
        return self.titles[session_id]


class ListProposalsUseCase:
    """Changes to check (memory spec §6.1, §8): the member's to answer, then the ones raised in their chats
    that someone else answers, read-only. Only the speaker answers; it counts only in their list."""

    def __init__(
        self,
        proposal_repo: IMemoryProposalRepository,
        memory_repo: IMemoryRepository,
        session_repo: ISessionRepository,
        user_repo: IUserRepository,
        agent_repo: IAgentRepository,
    ):
        self.proposal_repo = proposal_repo
        self.memory_repo = memory_repo
        self.session_repo = session_repo
        self.user_repo = user_repo
        self.agent_repo = agent_repo

    async def execute(self, member: User) -> ChangesToCheck:
        cards = _Cards(self.memory_repo, self.session_repo, MemoryItems(self.user_repo, self.agent_repo, member), member)
        mine = await self.proposal_repo.list_pending_for_answerer(member.id)
        others = await self.proposal_repo.list_pending_from_user(member.id)
        to_check, _ = await cards.split(mine)
        _, waiting = await cards.split(others)
        logger.info(
            "Changes to check listed | member=%s to_check=%d waiting=%d", member.id, len(to_check), len(waiting)
        )
        return ChangesToCheck(to_check=to_check, waiting=waiting)


class LookedOverUseCase:
    """A chat's last look-over (memory spec §8): the quiet line's counts, and the sheet it opens.

    Remembered is what that look-over saved and is still current, with the memories its answered changes
    saved. A secret chat, or one never looked over, has neither.
    """

    def __init__(
        self,
        proposal_repo: IMemoryProposalRepository,
        memory_repo: IMemoryRepository,
        session_repo: ISessionRepository,
        user_repo: IUserRepository,
        agent_repo: IAgentRepository,
    ):
        self.proposal_repo = proposal_repo
        self.memory_repo = memory_repo
        self.session_repo = session_repo
        self.user_repo = user_repo
        self.agent_repo = agent_repo

    async def summary(self, chat: ConversationSession, member: User) -> Optional[LookedOverSummary]:
        at = _looked_over_at(chat)
        if at is None or chat.user_id != member.id:
            return None
        proposals = await self._proposals(chat)
        remembered = await self._remembered(chat, proposals)
        to_check = [p for p in proposals if p.state == PENDING and p.answerer_id == member.id]
        return LookedOverSummary(
            through_message_id=chat.reflected_through_id, at=at, remembered=len(remembered), to_check=len(to_check)
        )

    async def execute(self, session_id: str, member: User) -> LookedOver:
        chat = await self.session_repo.get_by_id(session_id)
        at = _looked_over_at(chat) if chat is not None and chat.user_id == member.id else None
        if at is None:
            raise EntityNotFoundException("Nothing looked over in this chat")
        items = MemoryItems(self.user_repo, self.agent_repo, member)
        proposals = await self._proposals(chat)
        to_check, waiting = await _Cards(self.memory_repo, self.session_repo, items, member).split(proposals)
        remembered = [await items.item(m) for m in await self._remembered(chat, proposals)]
        logger.info(
            "Looked over opened | member=%s session=%s to_check=%d waiting=%d remembered=%d",
            member.id, session_id, len(to_check), len(waiting), len(remembered),
        )
        return LookedOver(at=at, to_check=to_check, waiting=waiting, remembered=remembered)

    async def _proposals(self, chat: ConversationSession) -> List[MemoryProposal]:
        return await self.proposal_repo.get_many(list((chat.last_reflection or {}).get("proposed") or []))

    async def _remembered(self, chat: ConversationSession, proposals: List[MemoryProposal]):
        ids = list((chat.last_reflection or {}).get("added") or [])
        ids += [p.new_memory_id for p in proposals if p.state in (BOTH, REPLACED) and p.new_memory_id]
        memories = [await self.memory_repo.get_by_id(i) for i in ids]
        return [m for m in memories if m is not None and m.is_active]


def _looked_over_at(chat: ConversationSession) -> Optional[datetime]:
    if chat.is_secret or chat.last_reflection is None or chat.reflected_at is None:
        return None
    return chat.reflected_at
