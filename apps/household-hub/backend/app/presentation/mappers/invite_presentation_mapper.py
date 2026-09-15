from app.domain.entities.invite import Invite, InvitePreview
from app.presentation.schemas.invite_schemas import InvitePreviewRead, InviteRead


class InvitePresentationMapper:
    @staticmethod
    def to_response(entity: Invite) -> InviteRead:
        return InviteRead(
            code=entity.code,
            invited_name=entity.invited_name,
            is_admin=entity.is_admin,
            expires_at=entity.expires_at,
        )

    @staticmethod
    def to_preview_response(preview: InvitePreview) -> InvitePreviewRead:
        return InvitePreviewRead(
            invited_name=preview.invited_name,
            inviter_name=preview.inviter_name,
            inviter_avatar_color=preview.inviter_avatar_color,
        )
