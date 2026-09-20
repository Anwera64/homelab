from datetime import datetime, timezone
from typing import Optional

from app.domain.entities.pin_reset import PinReset
from app.data.models.pin_reset_model import PinResetModel


def _as_utc(value: Optional[datetime]) -> Optional[datetime]:
    """SQLite hands timezone-aware columns back naive; they were written in UTC."""
    if value is not None and value.tzinfo is None:
        return value.replace(tzinfo=timezone.utc)
    return value


class PinResetDataMapper:
    @staticmethod
    def to_domain(model: PinResetModel) -> PinReset:
        return PinReset(
            id=model.id,
            code=model.code,
            target_user_id=model.target_user_id,
            approver_id=model.approver_id,
            expires_at=_as_utc(model.expires_at),
            used_at=_as_utc(model.used_at),
            created_at=_as_utc(model.created_at),
        )

    @staticmethod
    def to_model(entity: PinReset) -> PinResetModel:
        return PinResetModel(
            id=entity.id,
            code=entity.code,
            target_user_id=entity.target_user_id,
            approver_id=entity.approver_id,
            expires_at=entity.expires_at,
            used_at=entity.used_at,
            created_at=entity.created_at,
        )
