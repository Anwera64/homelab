from app.domain.entities.space import Space
from app.data.models.space_model import SpaceModel


class SpaceDataMapper:
    @staticmethod
    def to_domain(model: SpaceModel) -> Space:
        return Space(
            id=model.id,
            name=model.name,
            type=model.type,
            owner_id=model.owner_id,
            settings=model.settings or {},
            created_at=model.created_at,
            updated_at=model.updated_at,
        )

    @staticmethod
    def to_model(entity: Space) -> SpaceModel:
        return SpaceModel(
            id=entity.id,
            name=entity.name,
            type=entity.type,
            owner_id=entity.owner_id,
            settings=entity.settings or {},
            created_at=entity.created_at,
            updated_at=entity.updated_at,
        )
