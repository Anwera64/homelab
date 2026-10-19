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
            expires_at=model.expires_at,
            origin=model.origin,
            relevant_for=model.relevant_for,
            event_id=model.event_id,
            replaced_by_id=model.replaced_by_id,
            retired_at=model.retired_at,
            moved_from=model.moved_from,
            event_starts_at=model.event_starts_at,
            time_from_event=model.time_from_event,
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
            expires_at=entity.expires_at,
            origin=entity.origin,
            relevant_for=entity.relevant_for,
            event_id=entity.event_id,
            replaced_by_id=entity.replaced_by_id,
            retired_at=entity.retired_at,
            moved_from=entity.moved_from,
            event_starts_at=entity.event_starts_at,
            time_from_event=entity.time_from_event,
            created_at=entity.created_at,
            updated_at=entity.updated_at,
        )
