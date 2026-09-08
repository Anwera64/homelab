from app.domain.entities.space import Space
from app.presentation.schemas.space_schemas import SpaceRead


class SpacePresentationMapper:
    @staticmethod
    def to_response(entity: Space) -> SpaceRead:
        return SpaceRead(
            id=entity.id,
            name=entity.name,
            type=entity.type,
            owner_id=entity.owner_id,
            settings=entity.settings or {},
            created_at=entity.created_at,
        )
