import json
from datetime import datetime, timedelta, timezone
from urllib.parse import parse_qs, urlparse

import httpx
import jwt
import pytest

from app.data.connectors.google_oauth_client import CALENDAR_SCOPE, GoogleOAuthClient
from app.domain.exceptions import (
    CalendarOAuthRevokedException,
    CalendarSignInDeniedException,
    CalendarUnreachableException,
)

REDIRECT = "https://hub.example.org/api/v1/integrations/calendars/google/callback"


def _id_token(email: str) -> str:
    return jwt.encode({"email": email, "sub": "google-1"}, "google-signs-this-with-a-key-long-enough", algorithm="HS256")


def _client(handler) -> tuple[GoogleOAuthClient, list[httpx.Request]]:
    seen: list[httpx.Request] = []

    def record(request: httpx.Request) -> httpx.Response:
        seen.append(request)
        return handler(request)

    http = httpx.AsyncClient(transport=httpx.MockTransport(record))
    return GoogleOAuthClient("client-id", "client-secret", REDIRECT, client=http), seen


def _form(request: httpx.Request) -> dict:
    return {k: v[0] for k, v in parse_qs(request.content.decode()).items()}


def test_the_consent_url_asks_for_the_calendar_offline_and_comes_back_to_the_hub():
    """GIVEN a state WHEN the consent URL is built THEN it asks Google for a refresh token to the calendar and returns to the hub."""
    oauth, _ = _client(lambda r: httpx.Response(500))

    url = oauth.authorization_url("the-state")

    parsed = urlparse(url)
    query = {k: v[0] for k, v in parse_qs(parsed.query).items()}
    assert parsed.netloc == "accounts.google.com"
    assert query["client_id"] == "client-id"
    assert query["redirect_uri"] == REDIRECT
    assert query["response_type"] == "code"
    assert query["access_type"] == "offline"
    assert query["prompt"] == "consent"
    assert query["state"] == "the-state"
    assert set(query["scope"].split()) == {"openid", "email", CALENDAR_SCOPE}


@pytest.mark.asyncio
async def test_a_sign_in_code_becomes_tokens_and_the_account_email():
    """GIVEN Google accepts the code WHEN it is exchanged THEN the grant carries both tokens, the expiry and the email."""
    def google(request):
        return httpx.Response(200, json={
            "access_token": "access-1",
            "refresh_token": "refresh-1",
            "expires_in": 3599,
            "scope": f"openid {CALENDAR_SCOPE} https://www.googleapis.com/auth/userinfo.email",
            "id_token": _id_token("emma@gmail.com"),
        })
    oauth, seen = _client(google)
    before = datetime.now(timezone.utc)

    grant = await oauth.exchange_code("the-code")

    assert grant.access_token == "access-1"
    assert grant.refresh_token == "refresh-1"
    assert grant.email == "emma@gmail.com"
    assert before + timedelta(seconds=3590) <= grant.expires_at <= datetime.now(timezone.utc) + timedelta(seconds=3600)
    form = _form(seen[0])
    assert str(seen[0].url) == "https://oauth2.googleapis.com/token"
    assert form == {
        "code": "the-code",
        "client_id": "client-id",
        "client_secret": "client-secret",
        "redirect_uri": REDIRECT,
        "grant_type": "authorization_code",
    }


@pytest.mark.asyncio
async def test_a_sign_in_without_the_calendar_ticked_is_denied():
    """GIVEN the member unticked calendar access on the consent screen WHEN the code is exchanged THEN the sign-in is denied."""
    oauth, _ = _client(lambda r: httpx.Response(200, json={
        "access_token": "a", "refresh_token": "r", "expires_in": 3599,
        "scope": "openid https://www.googleapis.com/auth/userinfo.email",
        "id_token": _id_token("emma@gmail.com"),
    }))

    with pytest.raises(CalendarSignInDeniedException):
        await oauth.exchange_code("the-code")


@pytest.mark.asyncio
async def test_a_code_google_refuses_is_denied():
    """GIVEN Google refuses the code WHEN it is exchanged THEN the sign-in is denied."""
    oauth, _ = _client(lambda r: httpx.Response(400, json={"error": "invalid_grant"}))

    with pytest.raises(CalendarSignInDeniedException):
        await oauth.exchange_code("used-code")


@pytest.mark.asyncio
async def test_google_out_of_reach_during_the_exchange_is_unreachable():
    """GIVEN Google cannot be reached WHEN the code is exchanged THEN the calendar is unreachable."""
    def down(request):
        raise httpx.ConnectError("no route")
    oauth, _ = _client(down)

    with pytest.raises(CalendarUnreachableException):
        await oauth.exchange_code("the-code")


@pytest.mark.asyncio
async def test_a_refresh_gives_a_new_access_token():
    """GIVEN a live refresh token WHEN it is used THEN a new access token and expiry come back."""
    oauth, seen = _client(lambda r: httpx.Response(200, json={"access_token": "access-2", "expires_in": 3599}))

    grant = await oauth.refresh("refresh-1")

    assert grant.access_token == "access-2"
    assert grant.refresh_token is None
    assert grant.expires_at > datetime.now(timezone.utc) + timedelta(minutes=59)
    assert _form(seen[0]) == {
        "refresh_token": "refresh-1",
        "client_id": "client-id",
        "client_secret": "client-secret",
        "grant_type": "refresh_token",
    }


@pytest.mark.asyncio
async def test_a_revoked_refresh_token_says_so():
    """GIVEN Google no longer honours the refresh token WHEN it is used THEN it reads as revoked."""
    oauth, _ = _client(lambda r: httpx.Response(400, content=json.dumps({"error": "invalid_grant"})))

    with pytest.raises(CalendarOAuthRevokedException):
        await oauth.refresh("refresh-1")


@pytest.mark.asyncio
async def test_google_failing_during_a_refresh_is_unreachable_not_revoked():
    """GIVEN Google answers with a server error WHEN a refresh is tried THEN the token is kept and the calendar is unreachable."""
    oauth, _ = _client(lambda r: httpx.Response(503))

    with pytest.raises(CalendarUnreachableException):
        await oauth.refresh("refresh-1")
