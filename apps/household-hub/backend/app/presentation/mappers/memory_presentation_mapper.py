from app.domain.entities.memory import AgentMemory
from app.presentation.schemas.memory_schemas import MemoryRead


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
