from app.domain.entities.user import User
from app.data.models.user_model import UserModel


class UserDataMapper:
    @staticmethod
    def to_domain(model: UserModel) -> User:
        # Check __dict__ to avoid triggering lazy load in async SQLAlchemy
        personal_space = model.__dict__.get("personal_space")
        personal_space_id = personal_space.id if personal_space else None

        return User(
            id=model.id,
            username=model.username,
            email=model.email,
            full_name=model.full_name,
            hashed_password=model.hashed_password,
            avatar_color=model.avatar_color,
            is_admin=model.is_admin,
            is_active=model.is_active,
            personal_space_id=personal_space_id,
            created_at=model.created_at,
            updated_at=model.updated_at,
        )

    @staticmethod
    def to_model(entity: User) -> UserModel:
        return UserModel(
            id=entity.id,
            username=entity.username,
            email=entity.email,
            full_name=entity.full_name,
            hashed_password=entity.hashed_password,
            avatar_color=entity.avatar_color,
            is_admin=entity.is_admin,
            is_active=entity.is_active,
            created_at=entity.created_at,
            updated_at=entity.updated_at,
        )
