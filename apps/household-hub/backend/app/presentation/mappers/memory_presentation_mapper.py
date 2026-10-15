from app.domain.entities.memory import AgentMemory, MemoryPage, MemoryPageItem
from app.presentation.schemas.memory_schemas import MemoryPageItemRead, MemoryPageRead, MemoryRead


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
        )

    @staticmethod
    def to_page(page: MemoryPage) -> MemoryPageRead:
        return MemoryPageRead(
            yours=[MemoryPresentationMapper.to_page_item(i) for i in page.yours],
            household=[MemoryPresentationMapper.to_page_item(i) for i in page.household],
        )
