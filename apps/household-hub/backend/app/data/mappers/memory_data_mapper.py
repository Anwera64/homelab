from datetime import datetime, timezone
from typing import Optional

from app.domain.entities.memory import AgentMemory
from app.data.models.memory_model import MemoryModel


def as_stored(moment: Optional[datetime]) -> Optional[datetime]:
    """SQLite drops the zone without converting, so a time is turned into UTC before it is written.

    An event's times come from the calendar in its own zone (Europe/Madrid); stored as they are,
    20:00+02:00 would be read back as 20:00 UTC.
    """
    if moment is None or moment.tzinfo is None:
        return moment
    return moment.astimezone(timezone.utc)


def _as_utc(moment: Optional[datetime]) -> Optional[datetime]:
    """SQLite hands timezone-aware columns back naive; they were written in UTC."""
    if moment is None or moment.tzinfo is not None:
        return moment
    return moment.replace(tzinfo=timezone.utc)


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
            expires_at=_as_utc(model.expires_at),
            origin=model.origin,
            relevant_for=model.relevant_for,
            event_id=model.event_id,
            event_calendar=model.event_calendar,
            replaced_by_id=model.replaced_by_id,
            retired_at=_as_utc(model.retired_at),
            moved_from=_as_utc(model.moved_from),
            event_starts_at=_as_utc(model.event_starts_at),
            time_from_event=model.time_from_event,
            created_at=_as_utc(model.created_at),
            updated_at=_as_utc(model.updated_at),
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
            expires_at=as_stored(entity.expires_at),
            origin=entity.origin,
            relevant_for=entity.relevant_for,
            event_id=entity.event_id,
            event_calendar=entity.event_calendar,
            replaced_by_id=entity.replaced_by_id,
            retired_at=as_stored(entity.retired_at),
            moved_from=as_stored(entity.moved_from),
            event_starts_at=as_stored(entity.event_starts_at),
            time_from_event=entity.time_from_event,
            created_at=as_stored(entity.created_at),
            updated_at=as_stored(entity.updated_at),
        )
