from app.data.models.calendar_source_model import CalendarSourceModel
from app.domain.entities.calendar_source import CalendarSource


class CalendarSourceDataMapper:
    @staticmethod
    def to_domain(model: CalendarSourceModel) -> CalendarSource:
        return CalendarSource(
            key=model.key, url=model.url, name=model.name, writable=model.writable, picked=model.picked
        )

    @staticmethod
    def to_model(entity: CalendarSource, credential_id: str, position: int) -> CalendarSourceModel:
        return CalendarSourceModel(
            credential_id=credential_id,
            key=entity.key,
            url=entity.url,
            name=entity.name,
            writable=entity.writable,
            picked=entity.picked,
            position=position,
        )
