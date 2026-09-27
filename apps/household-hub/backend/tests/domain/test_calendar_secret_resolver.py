from datetime import datetime, timedelta, timezone
from typing import List, Optional

import pytest

from app.domain.entities.integration_credential import CalendarCredential
from app.domain.entities.oauth_grant import OAuthGrant
from app.domain.exceptions import (
    CalendarAuthException,
    CalendarOAuthRevokedException,
    CalendarUnreachableException,
)
from app.domain.use_cases.integrations.calendar_secret_resolver import CalendarSecretResolver
from app.domain.use_cases.integrations.create_calendar_event import CreateCalendarEventUseCase
from app.domain.use_cases.integrations.delete_calendar_event import DeleteCalendarEventUseCase
from app.domain.use_cases.integrations.execute_tool import ExecuteToolUseCase
from app.domain.use_cases.integrations.get_calendar_events import GetCalendarEventsUseCase
from app.domain.use_cases.integrations.update_calendar_event import UpdateCalendarEventUseCase
from tests.domain.test_integration_use_cases import (
    MockCalendarConnector,
    MockCalendarCredentialRepository,
    MockDocumentReader,
    MockDocumentRepository,
    MockSearchConnector,
    MockSecretCipher,
    MockUnitOfWork,
)

NOW = datetime(2026, 9, 27, 12, 0, tzinfo=timezone.utc)


class FakeGoogleOAuth:
    def __init__(self, grant: Optional[OAuthGrant] = None, error: Optional[Exception] = None):
        self.grant = grant
        self.error = error
        self.refreshed: List[str] = []

    def authorization_url(self, state: str) -> str:
        return f"https://accounts.google.com/?state={state}"

    async def exchange_code(self, code: str) -> OAuthGrant:
        raise NotImplementedError

    async def refresh(self, refresh_token: str) -> OAuthGrant:
        self.refreshed.append(refresh_token)
        if self.error:
            raise self.error
        return self.grant


def _google(expires_at: datetime) -> CalendarCredential:
    return CalendarCredential(
        user_id="member-1",
        provider="google_caldav",
        url="https://apidata.googleusercontent.com/caldav/v2/emma@gmail.com/events",
        username="emma@gmail.com",
        encrypted_secret="ENC:access-1",
        auth_kind="oauth",
        encrypted_refresh_token="ENC:refresh-1",
        token_expires_at=expires_at,
    )


def _resolver(repo, oauth=None, uow=None) -> CalendarSecretResolver:
    return CalendarSecretResolver(repo, MockSecretCipher(), uow or MockUnitOfWork(), oauth, clock=lambda: NOW)


@pytest.mark.asyncio
async def test_a_password_calendar_sends_its_password():
    """GIVEN a calendar connected with a password WHEN its secret is needed THEN the password comes back."""
    repo = MockCalendarCredentialRepository()
    credential = CalendarCredential(
        user_id="member-1", provider="apple_icloud", url="https://caldav.icloud.com",
        username="emma@icloud.com", encrypted_secret="ENC:app-password",
    )

    secret = await _resolver(repo).resolve(credential)

    assert secret == "app-password"


@pytest.mark.asyncio
async def test_a_fresh_google_token_is_used_without_asking_google():
    """GIVEN a Google calendar whose access token has time left WHEN its secret is needed THEN that token is sent as it is."""
    oauth = FakeGoogleOAuth()

    secret = await _resolver(MockCalendarCredentialRepository(), oauth).resolve(_google(NOW + timedelta(minutes=30)))

    assert secret == "access-1"
    assert oauth.refreshed == []


@pytest.mark.asyncio
async def test_a_google_token_about_to_expire_is_refreshed_and_kept():
    """GIVEN a Google calendar whose token runs out within a minute WHEN its secret is needed THEN a new token is fetched and saved."""
    repo = MockCalendarCredentialRepository()
    uow = MockUnitOfWork()
    new_expiry = NOW + timedelta(hours=1)
    oauth = FakeGoogleOAuth(grant=OAuthGrant(access_token="access-2", expires_at=new_expiry))

    secret = await _resolver(repo, oauth, uow).resolve(_google(NOW + timedelta(seconds=30)))

    assert secret == "access-2"
    assert oauth.refreshed == ["refresh-1"]
    saved = repo.credentials["member-1"]
    assert saved.encrypted_secret == "ENC:access-2"
    assert saved.token_expires_at == new_expiry
    assert saved.encrypted_refresh_token == "ENC:refresh-1"
    assert uow.committed


@pytest.mark.asyncio
async def test_a_revoked_google_sign_in_asks_the_member_to_sign_in_again():
    """GIVEN Google no longer honours the refresh token WHEN the secret is needed THEN the calendar is flagged for reconnecting and the call is refused."""
    repo = MockCalendarCredentialRepository()
    oauth = FakeGoogleOAuth(error=CalendarOAuthRevokedException("invalid_grant"))

    with pytest.raises(CalendarAuthException, match="sign in with Google again"):
        await _resolver(repo, oauth).resolve(_google(NOW - timedelta(minutes=5)))

    assert repo.credentials["member-1"].needs_reconnect is True


@pytest.mark.asyncio
async def test_google_out_of_reach_during_a_refresh_leaves_the_calendar_connected():
    """GIVEN Google can't be reached WHEN a token refresh is needed THEN the call fails as unreachable and nothing is flagged."""
    repo = MockCalendarCredentialRepository()
    oauth = FakeGoogleOAuth(error=CalendarUnreachableException("down"))

    with pytest.raises(CalendarUnreachableException):
        await _resolver(repo, oauth).resolve(_google(NOW - timedelta(minutes=5)))

    assert "member-1" not in repo.credentials


@pytest.mark.asyncio
async def test_a_calendar_already_flagged_is_refused_without_asking_google():
    """GIVEN a Google calendar already flagged for reconnecting WHEN its secret is needed THEN the call is refused straight away."""
    oauth = FakeGoogleOAuth()
    credential = _google(NOW - timedelta(minutes=5))
    credential.needs_reconnect = True

    with pytest.raises(CalendarAuthException, match="sign in with Google again"):
        await _resolver(MockCalendarCredentialRepository(), oauth).resolve(credential)

    assert oauth.refreshed == []


@pytest.mark.asyncio
async def test_a_google_calendar_on_a_hub_without_google_sign_in_is_refused():
    """GIVEN the hub has no Google client configured WHEN a Google calendar's expired token is needed THEN the call is refused."""
    with pytest.raises(CalendarAuthException):
        await _resolver(MockCalendarCredentialRepository(), oauth=None).resolve(_google(NOW - timedelta(minutes=5)))


class RecordingConnector(MockCalendarConnector):
    """Remembers the secret every call was made with."""

    def __init__(self):
        super().__init__()
        self.secrets: List[str] = []

    async def fetch_events(self, credential, secret, *args, **kwargs):
        self.secrets.append(secret)
        return await super().fetch_events(credential, secret, *args, **kwargs)

    async def create_event(self, credential, secret, *args, **kwargs):
        self.secrets.append(secret)
        return await super().create_event(credential, secret, *args, **kwargs)

    async def update_event(self, credential, secret, *args, **kwargs):
        self.secrets.append(secret)
        return await super().update_event(credential, secret, *args, **kwargs)

    async def delete_event(self, credential, secret, *args, **kwargs):
        self.secrets.append(secret)
        return await super().delete_event(credential, secret, *args, **kwargs)


async def _expired_google_calendar():
    repo = MockCalendarCredentialRepository()
    await repo.save(_google(NOW - timedelta(minutes=5)))
    oauth = FakeGoogleOAuth(grant=OAuthGrant(access_token="access-2", expires_at=NOW + timedelta(hours=1)))
    return repo, _resolver(repo, oauth)


@pytest.mark.asyncio
async def test_every_calendar_call_sends_a_refreshed_google_token():
    """GIVEN a Google calendar whose token has run out WHEN events are read, created, updated and deleted THEN each call sends a fresh token."""
    repo, secrets = await _expired_google_calendar()
    connector = RecordingConnector()
    start = NOW + timedelta(days=1)

    event = await CreateCalendarEventUseCase(repo, connector, secrets).execute("member-1", "Dentist", start, start)
    await GetCalendarEventsUseCase(repo, connector, secrets).execute("member-1", start, start)
    await UpdateCalendarEventUseCase(repo, connector, secrets).execute("member-1", event.id, title="Dentist (moved)")
    await DeleteCalendarEventUseCase(repo, connector, secrets).execute("member-1", event.id)

    assert connector.secrets == ["access-2"] * 4


@pytest.mark.asyncio
async def test_an_agent_reading_a_google_calendar_sends_a_refreshed_token():
    """GIVEN a Google calendar whose token has run out WHEN an agent reads it THEN the call sends a fresh token."""
    repo, secrets = await _expired_google_calendar()
    connector = RecordingConnector()
    tools = ExecuteToolUseCase(
        calendar_repo=repo,
        calendar_connector=connector,
        search_connector=MockSearchConnector(),
        document_repo=MockDocumentRepository(),
        document_reader=MockDocumentReader(),
        cipher=MockSecretCipher(),
        uow=MockUnitOfWork(),
        calendar_secrets=secrets,
    )

    result = await tools.execute(
        tool_name="calendar_read",
        arguments={"start_time": NOW.isoformat(), "end_time": (NOW + timedelta(days=7)).isoformat()},
        user_id="member-1",
        agent_tool_permissions=["calendar_read"],
    )

    assert result.success, result.error
    assert connector.secrets == ["access-2"]
