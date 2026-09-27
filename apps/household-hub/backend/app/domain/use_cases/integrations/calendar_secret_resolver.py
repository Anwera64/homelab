from dataclasses import replace
from datetime import datetime, timedelta, timezone
from typing import Callable, Optional

from app.domain.entities.integration_credential import OAUTH, CalendarCredential
from app.domain.exceptions import CalendarAuthException, CalendarOAuthRevokedException
from app.domain.repositories.calendar_credential_repository import ICalendarCredentialRepository
from app.domain.repositories.google_oauth_client import IGoogleOAuthClient
from app.domain.repositories.secret_cipher import ISecretCipher
from app.domain.repositories.unit_of_work import IUnitOfWork

# Refresh a little early, so a token never runs out halfway through a CalDAV call.
REFRESH_MARGIN = timedelta(seconds=60)
SIGN_IN_AGAIN = "Google no longer accepts this calendar's sign-in; the member has to sign in with Google again."


class CalendarSecretResolver:
    """
    The secret a calendar call sends: the password, or a Google access token that is refreshed
    when it runs out. A sign-in Google has revoked flags the calendar so Profile can ask for a new one.
    """

    def __init__(
        self,
        credential_repo: ICalendarCredentialRepository,
        cipher: ISecretCipher,
        uow: IUnitOfWork,
        oauth: Optional[IGoogleOAuthClient] = None,
        clock: Callable[[], datetime] = lambda: datetime.now(timezone.utc),
    ):
        self.credential_repo = credential_repo
        self.cipher = cipher
        self.uow = uow
        self.oauth = oauth
        self.clock = clock

    async def resolve(self, credential: CalendarCredential) -> str:
        if credential.auth_kind != OAUTH:
            return self.cipher.decrypt(credential.encrypted_secret)
        if credential.needs_reconnect:
            raise CalendarAuthException(SIGN_IN_AGAIN)
        if credential.token_expires_at and credential.token_expires_at - REFRESH_MARGIN > self.clock():
            return self.cipher.decrypt(credential.encrypted_secret)
        if self.oauth is None or not credential.encrypted_refresh_token:
            raise CalendarAuthException("This hub has no Google sign-in configured to refresh the calendar.")

        try:
            grant = await self.oauth.refresh(self.cipher.decrypt(credential.encrypted_refresh_token))
        except CalendarOAuthRevokedException:
            await self._save(replace(credential, needs_reconnect=True, updated_at=self.clock()))
            raise CalendarAuthException(SIGN_IN_AGAIN)

        await self._save(
            replace(
                credential,
                encrypted_secret=self.cipher.encrypt(grant.access_token),
                token_expires_at=grant.expires_at,
                updated_at=self.clock(),
            )
        )
        return grant.access_token

    async def _save(self, credential: CalendarCredential) -> None:
        async with self.uow:
            await self.credential_repo.save(credential)
            await self.uow.commit()
