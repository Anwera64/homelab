from app.domain.entities.memory import AgentMemory
from app.data.models.memory_model import MemoryModel


class MemoryDataMapper:
    @staticmethod
    def to_domain(model: MemoryModel) -> AgentMemory:
        return AgentMemory(
            id=model.id,
            user_id=model.user_id,
            agent_id=model.agent_id,
            scope=model.scope,
            category=model.category,
            content=model.content,
            confidence=model.confidence,
            source_session_id=model.source_session_id,
            is_active=model.is_active,
            created_at=model.created_at,
            updated_at=model.updated_at,
        )

    @staticmethod
    def to_model(entity: AgentMemory) -> MemoryModel:
        return MemoryModel(
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
