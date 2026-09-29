from typing import Optional

from app.domain.entities.integration_credential import OAUTH, CalendarCredential
from app.domain.exceptions import CalendarSignInDeniedException, CalendarSignInNotConfiguredException
from app.domain.repositories.calendar_connector import ICalendarConnector
from app.domain.repositories.calendar_credential_repository import ICalendarCredentialRepository
from app.domain.repositories.google_oauth_client import IGoogleOAuthClient
from app.domain.repositories.secret_cipher import ISecretCipher
from app.domain.repositories.sign_in_state_service import ISignInStateService
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.use_cases.integrations.mark_calendar_steps_fixed import MarkCalendarStepsFixedUseCase

GOOGLE_PROVIDER = "google_caldav"
GOOGLE_CALDAV_PREFIX = "https://apidata.googleusercontent.com/caldav/v2/"


def google_calendar_url(email: str) -> str:
    """The member's primary Google calendar: Google names it after the account."""
    return f"{GOOGLE_CALDAV_PREFIX}{email}/events"


class StartGoogleCalendarSignInUseCase:
    def __init__(self, states: ISignInStateService, oauth: Optional[IGoogleOAuthClient]):
        self.states = states
        self.oauth = oauth

    def execute(self, user_id: str) -> str:
        if self.oauth is None:
            raise CalendarSignInNotConfiguredException("This hub has no Google sign-in configured.")
        return self.oauth.authorization_url(self.states.issue(user_id))


class CompleteGoogleCalendarSignInUseCase:
    """
    Google's callback: works out whose sign-in it is from the state, swaps the code for tokens, checks
    the calendar answers to them, and only then replaces the member's calendar.
    """

    def __init__(
        self,
        states: ISignInStateService,
        oauth: Optional[IGoogleOAuthClient],
        connector: ICalendarConnector,
        cipher: ISecretCipher,
        credential_repo: ICalendarCredentialRepository,
        uow: IUnitOfWork,
        mark_fixed: Optional[MarkCalendarStepsFixedUseCase] = None,
    ):
        self.states = states
        self.oauth = oauth
        self.connector = connector
        self.cipher = cipher
        self.credential_repo = credential_repo
        self.uow = uow
        self.mark_fixed = mark_fixed

    async def execute(self, state: str, code: Optional[str], error: Optional[str] = None) -> CalendarCredential:
        user_id = self.states.verify(state)
        if self.oauth is None:
            raise CalendarSignInNotConfiguredException("This hub has no Google sign-in configured.")
        if error or not code:
            raise CalendarSignInDeniedException(f"Google sign-in did not finish: {error or 'no code'}.")

        grant = await self.oauth.exchange_code(code)
        if not grant.email:
            raise CalendarSignInDeniedException("Google did not say which account signed in.")

        candidate = CalendarCredential(
            user_id=user_id,
            provider=GOOGLE_PROVIDER,
            url=google_calendar_url(grant.email),
            username=grant.email,
            encrypted_secret=self.cipher.encrypt(grant.access_token),
            auth_kind=OAUTH,
            encrypted_refresh_token=self.cipher.encrypt(grant.refresh_token),
            token_expires_at=grant.expires_at,
        )
        await self.connector.test_connection(candidate, grant.access_token)

        async with self.uow:
            saved = await self.credential_repo.save(candidate)
            # The calendar steps that failed for want of one now read as fixed, saved together with it.
            if self.mark_fixed is not None:
                await self.mark_fixed.execute(user_id)
            await self.uow.commit()
        return saved
