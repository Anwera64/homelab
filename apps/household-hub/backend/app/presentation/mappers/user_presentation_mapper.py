from app.domain.entities.user import User
from app.presentation.schemas.user_schemas import UserRead


class UserPresentationMapper:
    @staticmethod
    def to_response(entity: User) -> UserRead:
        return UserRead(
            id=entity.id,
            username=entity.username,
            email=entity.email,
            full_name=entity.full_name,
            avatar_color=entity.avatar_color,
            is_admin=entity.is_admin,
            is_active=entity.is_active,
            personal_space_id=entity.personal_space_id,
            created_at=entity.created_at,
        )
