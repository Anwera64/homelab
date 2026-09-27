from datetime import datetime, timedelta, timezone
from typing import Optional
from urllib.parse import urlencode

import httpx
import jwt

from app.domain.entities.oauth_grant import OAuthGrant
from app.domain.exceptions import (
    CalendarOAuthRevokedException,
    CalendarSignInDeniedException,
    CalendarUnreachableException,
)
from app.domain.repositories.google_oauth_client import IGoogleOAuthClient

AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar"
SCOPES = ("openid", "email", CALENDAR_SCOPE)


class GoogleOAuthClient(IGoogleOAuthClient):
    """
    Google's OAuth 2.0 web-server flow. The hub is a confidential client: it holds the secret and
    swaps codes itself, so no token ever reaches the phone.
    """

    def __init__(
        self,
        client_id: str,
        client_secret: str,
        redirect_uri: str,
        client: Optional[httpx.AsyncClient] = None,
        timeout: float = 10.0,
    ):
        self.client_id = client_id
        self.client_secret = client_secret
        self.redirect_uri = redirect_uri
        self.client = client or httpx.AsyncClient(timeout=timeout)

    def authorization_url(self, state: str) -> str:
        query = {
            "client_id": self.client_id,
            "redirect_uri": self.redirect_uri,
            "response_type": "code",
            "scope": " ".join(SCOPES),
            # offline + consent: Google only returns a refresh token when it asks for consent.
            "access_type": "offline",
            "prompt": "consent",
            "include_granted_scopes": "true",
            "state": state,
        }
        return f"{AUTHORIZATION_ENDPOINT}?{urlencode(query)}"

    async def exchange_code(self, code: str) -> OAuthGrant:
        response = await self._post({
            "code": code,
            "client_id": self.client_id,
            "client_secret": self.client_secret,
            "redirect_uri": self.redirect_uri,
            "grant_type": "authorization_code",
        })
        if response.status_code != 200:
            raise CalendarSignInDeniedException(f"Google refused the sign-in code ({response.status_code}).")

        body = response.json()
        # Google's consent screen lets the member untick calendar access and still finish.
        if CALENDAR_SCOPE not in body.get("scope", "").split():
            raise CalendarSignInDeniedException("Calendar access was not granted.")
        if not body.get("refresh_token"):
            raise CalendarSignInDeniedException("Google did not grant offline access.")

        return OAuthGrant(
            access_token=body["access_token"],
            expires_at=_expiry(body),
            refresh_token=body["refresh_token"],
            email=_email(body.get("id_token")),
        )

    async def refresh(self, refresh_token: str) -> OAuthGrant:
        response = await self._post({
            "refresh_token": refresh_token,
            "client_id": self.client_id,
            "client_secret": self.client_secret,
            "grant_type": "refresh_token",
        })
        if response.status_code == 400 and _error(response) == "invalid_grant":
            raise CalendarOAuthRevokedException("Google no longer honours the calendar's refresh token.")
        if response.status_code != 200:
            raise CalendarUnreachableException(f"Google could not refresh the calendar token ({response.status_code}).")

        body = response.json()
        return OAuthGrant(access_token=body["access_token"], expires_at=_expiry(body))

    async def _post(self, form: dict) -> httpx.Response:
        try:
            return await self.client.post(TOKEN_ENDPOINT, data=form)
        except httpx.HTTPError as e:
            raise CalendarUnreachableException(f"Google could not be reached: {e}")


def _expiry(body: dict) -> datetime:
    return datetime.now(timezone.utc) + timedelta(seconds=int(body.get("expires_in", 3600)))


def _email(id_token: Optional[str]) -> Optional[str]:
    if not id_token:
        return None
    # The id_token came straight from Google's token endpoint over TLS, so its signature needs no check
    # (Google's own guidance for tokens received this way).
    claims = jwt.decode(id_token, options={"verify_signature": False})
    return claims.get("email")


def _error(response: httpx.Response) -> Optional[str]:
    try:
        return response.json().get("error")
    except ValueError:
        return None
