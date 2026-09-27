from datetime import datetime, timedelta, timezone
from typing import Optional

import pytest

from app.domain.entities.integration_credential import CalendarCredential
from app.domain.entities.oauth_grant import OAuthGrant
from app.domain.exceptions import (
    CalendarAuthException,
    CalendarSignInDeniedException,
    CalendarSignInExpiredException,
    CalendarSignInNotConfiguredException,
    CalendarUnreachableException,
)
from app.domain.use_cases.integrations.google_calendar_sign_in import (
    CompleteGoogleCalendarSignInUseCase,
    StartGoogleCalendarSignInUseCase,
)
from tests.domain.test_integration_use_cases import (
    MockCalendarConnector,
    MockCalendarCredentialRepository,
    MockSecretCipher,
    MockUnitOfWork,
)

EXPIRY = datetime(2026, 9, 27, 13, 0, tzinfo=timezone.utc)
GRANT = OAuthGrant(access_token="access-1", expires_at=EXPIRY, refresh_token="refresh-1", email="emma@gmail.com")


class FakeStates:
    def issue(self, user_id: str) -> str:
        return f"state-for-{user_id}"

    def verify(self, state: str) -> str:
        if not state.startswith("state-for-"):
            raise CalendarSignInExpiredException("not ours")
        return state.removeprefix("state-for-")


class FakeGoogle:
    def __init__(self, grant: OAuthGrant = GRANT, error: Optional[Exception] = None):
        self.grant = grant
        self.error = error
        self.codes: list[str] = []

    def authorization_url(self, state: str) -> str:
        return f"https://accounts.google.com/o/oauth2/v2/auth?state={state}"

    async def exchange_code(self, code: str) -> OAuthGrant:
        self.codes.append(code)
        if self.error:
            raise self.error
        return self.grant

    async def refresh(self, refresh_token: str) -> OAuthGrant:
        raise NotImplementedError


class RecordingConnector(MockCalendarConnector):
    def __init__(self, connection_error: Optional[Exception] = None):
        super().__init__(connection_error=connection_error)
        self.tested: list[tuple[CalendarCredential, str]] = []

    async def test_connection(self, credential, secret):
        self.tested.append((credential, secret))
        return await super().test_connection(credential, secret)


def _complete(repo, google=None, connector=None, uow=None) -> CompleteGoogleCalendarSignInUseCase:
    return CompleteGoogleCalendarSignInUseCase(
        states=FakeStates(),
        oauth=google or FakeGoogle(),
        connector=connector or RecordingConnector(),
        cipher=MockSecretCipher(),
        credential_repo=repo,
        uow=uow or MockUnitOfWork(),
    )


def test_starting_a_sign_in_sends_the_member_to_google_with_their_state():
    """GIVEN a member WHEN they start a Google sign-in THEN they get Google's consent page carrying a state tied to them."""
    url = StartGoogleCalendarSignInUseCase(FakeStates(), FakeGoogle()).execute("member-1")

    assert url == "https://accounts.google.com/o/oauth2/v2/auth?state=state-for-member-1"


def test_starting_a_sign_in_on_a_hub_without_google_is_refused():
    """GIVEN the hub has no Google client WHEN a member starts a sign-in THEN it is refused as not configured."""
    with pytest.raises(CalendarSignInNotConfiguredException):
        StartGoogleCalendarSignInUseCase(FakeStates(), None).execute("member-1")


@pytest.mark.asyncio
async def test_a_finished_sign_in_connects_the_members_google_calendar():
    """GIVEN a member with a password calendar WHEN their Google sign-in comes back THEN a Google calendar with its tokens replaces it."""
    repo = MockCalendarCredentialRepository()
    await repo.save(CalendarCredential(
        user_id="member-1", provider="apple_icloud", url="https://caldav.icloud.com",
        username="emma@icloud.com", encrypted_secret="ENC:app-password",
    ))
    connector = RecordingConnector()
    uow = MockUnitOfWork()

    saved = await _complete(repo, connector=connector, uow=uow).execute(state="state-for-member-1", code="the-code")

    assert saved == repo.credentials["member-1"]
    assert saved.provider == "google_caldav"
    assert saved.auth_kind == "oauth"
    assert saved.username == "emma@gmail.com"
    assert saved.url == "https://apidata.googleusercontent.com/caldav/v2/emma@gmail.com/events"
    assert saved.encrypted_secret == "ENC:access-1"
    assert saved.encrypted_refresh_token == "ENC:refresh-1"
    assert saved.token_expires_at == EXPIRY
    assert saved.needs_reconnect is False
    assert connector.tested[0][1] == "access-1"
    assert uow.committed


@pytest.mark.asyncio
async def test_a_sign_in_with_a_state_the_hub_did_not_issue_is_refused_before_google_is_asked():
    """GIVEN a callback with a foreign state WHEN it arrives THEN it is refused as expired and the code is never used."""
    repo = MockCalendarCredentialRepository()
    google = FakeGoogle()

    with pytest.raises(CalendarSignInExpiredException):
        await _complete(repo, google=google).execute(state="forged", code="the-code")

    assert google.codes == []
    assert repo.credentials == {}


@pytest.mark.asyncio
async def test_a_member_saying_no_to_google_is_denied():
    """GIVEN the member cancelled on Google's consent screen WHEN the callback arrives with an error THEN the sign-in is denied."""
    repo = MockCalendarCredentialRepository()
    google = FakeGoogle()

    with pytest.raises(CalendarSignInDeniedException):
        await _complete(repo, google=google).execute(state="state-for-member-1", code=None, error="access_denied")

    assert google.codes == []
    assert repo.credentials == {}


@pytest.mark.asyncio
async def test_a_grant_without_an_email_is_denied():
    """GIVEN Google did not say which account signed in WHEN the sign-in completes THEN it is denied, as the calendar has no address."""
    repo = MockCalendarCredentialRepository()
    google = FakeGoogle(grant=OAuthGrant(access_token="a", expires_at=EXPIRY, refresh_token="r", email=None))

    with pytest.raises(CalendarSignInDeniedException):
        await _complete(repo, google=google).execute(state="state-for-member-1", code="the-code")

    assert repo.credentials == {}


@pytest.mark.parametrize("error", [
    CalendarSignInDeniedException("code refused"),
    CalendarUnreachableException("google down"),
])
@pytest.mark.asyncio
async def test_a_code_exchange_that_fails_keeps_nothing(error):
    """GIVEN Google fails the code exchange WHEN the sign-in completes THEN the failure is passed on and nothing is saved."""
    repo = MockCalendarCredentialRepository()

    with pytest.raises(type(error)):
        await _complete(repo, google=FakeGoogle(error=error)).execute(state="state-for-member-1", code="the-code")

    assert repo.credentials == {}


@pytest.mark.parametrize("error", [
    CalendarAuthException("401 from caldav"),
    CalendarUnreachableException("caldav down"),
])
@pytest.mark.asyncio
async def test_a_calendar_that_does_not_answer_the_new_token_keeps_nothing(error):
    """GIVEN Google's CalDAV refuses or can't be reached WHEN the sign-in completes THEN the failure is passed on and nothing is saved."""
    repo = MockCalendarCredentialRepository()

    with pytest.raises(type(error)):
        await _complete(repo, connector=RecordingConnector(connection_error=error)).execute(
            state="state-for-member-1", code="the-code"
        )

    assert repo.credentials == {}
