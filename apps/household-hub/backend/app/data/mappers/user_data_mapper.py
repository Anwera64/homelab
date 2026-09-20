from datetime import datetime, timezone
from typing import Optional

from app.domain.entities.user import User
from app.data.models.user_model import UserModel


def _as_utc(value: Optional[datetime]) -> Optional[datetime]:
    """SQLite hands timezone-aware columns back naive; they were written in UTC."""
    if value is not None and value.tzinfo is None:
        return value.replace(tzinfo=timezone.utc)
    return value


class UserDataMapper:
    @staticmethod
    def to_domain(model: UserModel) -> User:
        # Check __dict__ to avoid triggering lazy load in async SQLAlchemy
        personal_space = model.__dict__.get("personal_space")
        personal_space_id = personal_space.id if personal_space else None

        return User(
            id=model.id,
            full_name=model.full_name,
            hashed_pin=model.hashed_pin,
            avatar_color=model.avatar_color,
            is_admin=model.is_admin,
            is_active=model.is_active,
            failed_pin_attempts=model.failed_pin_attempts or 0,
            pin_locked_until=_as_utc(model.pin_locked_until),
            token_version=model.token_version or 0,
            personal_space_id=personal_space_id,
            created_at=model.created_at,
            updated_at=model.updated_at,
        )

    @staticmethod
    def to_model(entity: User) -> UserModel:
        return UserModel(
            id=entity.id,
            full_name=entity.full_name,
            hashed_pin=entity.hashed_pin,
            avatar_color=entity.avatar_color,
            is_admin=entity.is_admin,
            is_active=entity.is_active,
            failed_pin_attempts=entity.failed_pin_attempts,
            pin_locked_until=entity.pin_locked_until,
            token_version=entity.token_version,
            created_at=entity.created_at,
            updated_at=entity.updated_at,
        )
