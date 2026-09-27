from dataclasses import dataclass
from datetime import datetime
from typing import Optional


@dataclass(frozen=True)
class OAuthGrant:
    """
    What Google hands back for a sign-in or a refresh. A refresh carries no new refresh token and no
    email, so both are optional.
    """

    access_token: str
    expires_at: datetime
    refresh_token: Optional[str] = None
    email: Optional[str] = None
