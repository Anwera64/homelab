from datetime import datetime, timezone
from typing import Optional

from app.domain.entities.invite import Invite
from app.data.models.invite_model import InviteModel


def _as_utc(value: Optional[datetime]) -> Optional[datetime]:
    """SQLite hands timezone-aware columns back naive; they were written in UTC."""
    if value is not None and value.tzinfo is None:
        return value.replace(tzinfo=timezone.utc)
    return value


class InviteDataMapper:
    @staticmethod
    def to_domain(model: InviteModel) -> Invite:
        return Invite(
            id=model.id,
            code=model.code,
            invited_name=model.invited_name,
            inviter_id=model.inviter_id,
            is_admin=model.is_admin,
            expires_at=_as_utc(model.expires_at),
            used_at=_as_utc(model.used_at),
            created_at=_as_utc(model.created_at),
        )

    @staticmethod
    def to_model(entity: Invite) -> InviteModel:
        return InviteModel(
            id=entity.id,
            code=entity.code,
            invited_name=entity.invited_name,
            inviter_id=entity.inviter_id,
            is_admin=entity.is_admin,
            expires_at=entity.expires_at,
            used_at=entity.used_at,
            created_at=entity.created_at,
        )
