from typing import Protocol

from app.domain.entities.oauth_grant import OAuthGrant


class IGoogleOAuthClient(Protocol):
    def authorization_url(self, state: str) -> str:
        """Google's consent page, asking for offline access to the member's calendar."""
        ...

    async def exchange_code(self, code: str) -> OAuthGrant:
        """
        Raises CalendarSignInDeniedException when Google refuses the code or calendar access was not
        granted, and CalendarUnreachableException when Google can't be reached.
        """
        ...

    async def refresh(self, refresh_token: str) -> OAuthGrant:
        """
        Raises CalendarOAuthRevokedException when Google no longer honours the token, and
        CalendarUnreachableException for anything else, so a hiccup never loses the calendar.
        """
        ...
