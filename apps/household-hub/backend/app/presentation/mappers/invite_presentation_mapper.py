from app.domain.entities.invite import Invite
from app.presentation.schemas.invite_schemas import InviteRead


class InvitePresentationMapper:
    @staticmethod
    def to_response(entity: Invite) -> InviteRead:
        return InviteRead(
            code=entity.code,
            invited_name=entity.invited_name,
            is_admin=entity.is_admin,
            expires_at=entity.expires_at,
        )
