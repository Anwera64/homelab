from typing import Optional

from app.domain.entities.memory import AgentMemory, MemoryPage, MemoryPageItem
from app.domain.entities.memory_proposal import (
    ChangeCard,
    ChangesToCheck,
    LookedOver,
    LookedOverSummary,
    MemoryProposal,
)
from app.presentation.schemas.memory_schemas import (
    ChangeCardRead,
    ChangeSourceRead,
    ChangesToCheckRead,
    LookedOverRead,
    LookedOverSummaryRead,
    MemoryPageItemRead,
    MemoryPageRead,
    MemoryRead,
    ProposalRead,
    WaitingChangeRead,
)


class MemoryPresentationMapper:
    @staticmethod
    def to_response(entity: AgentMemory) -> MemoryRead:
        return MemoryRead(
            id=entity.id,
            user_id=entity.user_id,
            agent_id=entity.agent_id,
            scope=entity.scope,
            category=entity.category,
            content=entity.content,
            confidence=entity.confidence,
            source_session_id=entity.source_session_id,
            is_active=entity.is_active,
            created_at=entity.created_at,
            updated_at=entity.updated_at,
        )

    @staticmethod
    def to_page_item(item: MemoryPageItem) -> MemoryPageItemRead:
        memory = item.memory
        return MemoryPageItemRead(
            id=memory.id,
            content=memory.content,
            scope=memory.scope,
            origin=memory.origin,
            created_at=memory.created_at,
            expires_at=memory.expires_at,
            speaker_id=memory.user_id,
            speaker_name=item.speaker_name,
            is_mine=item.is_mine,
            agent_name=item.agent_name,
            agent_avatar=item.agent_avatar,
            can_remove=item.can_remove,
            event_starts_at=memory.event_starts_at if memory.time_from_event else None,
            time_from_event=memory.time_from_event,
            moved_from=memory.moved_from,
        )

    @staticmethod
    def to_page(page: MemoryPage) -> MemoryPageRead:
        return MemoryPageRead(
            yours=[MemoryPresentationMapper.to_page_item(i) for i in page.yours],
            household=[MemoryPresentationMapper.to_page_item(i) for i in page.household],
            to_check=page.to_check,
            waiting=[
                WaitingChangeRead(
                    id=w.proposal_id,
                    content=w.content,
                    agent_name=w.agent_name,
                    created_at=w.created_at,
                    expires_at=w.expires_at,
                    answerer_name=w.answerer_name,
                    stored=MemoryPresentationMapper.to_page_item(w.stored),
                )
                for w in page.waiting
            ],
        )

    @staticmethod
    def to_card(card: ChangeCard) -> ChangeCardRead:
        return ChangeCardRead(
            id=card.proposal.id,
            content=card.proposal.content,
            stored=MemoryPresentationMapper.to_page_item(card.stored),
            can_answer=card.can_answer,
            answerer_name=card.answerer_name,
            source=ChangeSourceRead(
                title=card.source_title,
                owner_name=card.source_owner_name,
                read_at=card.proposal.created_at,
            ),
        )

    @staticmethod
    def to_changes(changes: ChangesToCheck) -> ChangesToCheckRead:
        return ChangesToCheckRead(
            to_check=[MemoryPresentationMapper.to_card(c) for c in changes.to_check],
            waiting=[MemoryPresentationMapper.to_card(c) for c in changes.waiting],
        )

    @staticmethod
    def to_proposal(proposal: MemoryProposal) -> ProposalRead:
        return ProposalRead(
            id=proposal.id,
            memory_id=proposal.memory_id,
            content=proposal.content,
            state=proposal.state,
            new_memory_id=proposal.new_memory_id,
            answered_at=proposal.answered_at,
        )

    @staticmethod
    def to_looked_over_summary(summary: Optional[LookedOverSummary]) -> Optional[LookedOverSummaryRead]:
        if summary is None:
            return None
        return LookedOverSummaryRead(
            through_message_id=summary.through_message_id,
            at=summary.at,
            remembered=summary.remembered,
            to_check=summary.to_check,
        )

    @staticmethod
    def to_looked_over(looked_over: LookedOver) -> LookedOverRead:
        return LookedOverRead(
            at=looked_over.at,
            to_check=[MemoryPresentationMapper.to_card(c) for c in looked_over.to_check],
            waiting=[MemoryPresentationMapper.to_card(c) for c in looked_over.waiting],
            remembered=[MemoryPresentationMapper.to_page_item(i) for i in looked_over.remembered],
        )
