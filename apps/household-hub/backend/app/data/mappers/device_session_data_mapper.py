from datetime import datetime, timezone
from typing import Optional

from app.domain.entities.device_session import DeviceSession
from app.data.models.device_session_model import DeviceSessionModel


def _as_utc(value: Optional[datetime]) -> Optional[datetime]:
    """SQLite hands timezone-aware columns back naive; they were written in UTC."""
    if value is not None and value.tzinfo is None:
        return value.replace(tzinfo=timezone.utc)
    return value


class DeviceSessionDataMapper:
    @staticmethod
    def to_domain(model: DeviceSessionModel) -> DeviceSession:
        return DeviceSession(
            id=model.id,
            user_id=model.user_id,
            created_at=_as_utc(model.created_at),
            last_seen_at=_as_utc(model.last_seen_at),
        )

    @staticmethod
    def to_model(entity: DeviceSession) -> DeviceSessionModel:
        return DeviceSessionModel(
            id=entity.id,
            user_id=entity.user_id,
            created_at=entity.created_at,
            last_seen_at=entity.last_seen_at,
        )
