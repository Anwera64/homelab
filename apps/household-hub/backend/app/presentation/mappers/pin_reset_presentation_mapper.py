from app.domain.entities.pin_reset import PinReset
from app.presentation.schemas.pin_reset_schemas import PinResetRead


class PinResetPresentationMapper:
    @staticmethod
    def to_response(entity: PinReset) -> PinResetRead:
        return PinResetRead(code=entity.code, expires_at=entity.expires_at)
