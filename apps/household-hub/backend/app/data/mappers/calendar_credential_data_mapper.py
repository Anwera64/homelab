from app.domain.entities.integration_credential import CalendarCredential
from app.data.models.calendar_credential_model import CalendarCredentialModel


class CalendarCredentialDataMapper:
    @staticmethod
    def to_domain(model: CalendarCredentialModel) -> CalendarCredential:
        return CalendarCredential(
            id=model.id,
            user_id=model.user_id,
            provider=model.provider,
            url=model.url,
            username=model.username,
            encrypted_secret=model.encrypted_secret,
            calendar_name=model.calendar_name,
            is_active=model.is_active,
            auth_kind=model.auth_kind,
            encrypted_refresh_token=model.encrypted_refresh_token,
            token_expires_at=model.token_expires_at,
            needs_reconnect=model.needs_reconnect,
            created_at=model.created_at,
            updated_at=model.updated_at,
        )

    @staticmethod
    def to_model(entity: CalendarCredential) -> CalendarCredentialModel:
        return CalendarCredentialModel(
            id=entity.id,
            user_id=entity.user_id,
            provider=entity.provider,
            url=entity.url,
            username=entity.username,
            encrypted_secret=entity.encrypted_secret,
            calendar_name=entity.calendar_name,
            is_active=entity.is_active,
            auth_kind=entity.auth_kind,
            encrypted_refresh_token=entity.encrypted_refresh_token,
            token_expires_at=entity.token_expires_at,
            needs_reconnect=entity.needs_reconnect,
            created_at=entity.created_at,
            updated_at=entity.updated_at,
        )
