from datetime import datetime, timedelta, timezone

import jwt

from app.domain.exceptions import CalendarSignInExpiredException
from app.domain.repositories.sign_in_state_service import ISignInStateService

PURPOSE = "google_calendar_sign_in"


class JwtSignInStateService(ISignInStateService):
    """
    A signed, short-lived token, so the hub keeps no table of sign-ins in flight. The purpose claim
    stops a member's session token, signed with the same key, from passing as a state.
    """

    def __init__(self, secret_key: str, lifetime: timedelta = timedelta(minutes=10), algorithm: str = "HS256"):
        self.secret_key = secret_key
        self.lifetime = lifetime
        self.algorithm = algorithm

    def issue(self, user_id: str) -> str:
        now = datetime.now(timezone.utc)
        payload = {"sub": user_id, "purpose": PURPOSE, "iat": now, "exp": now + self.lifetime}
        return jwt.encode(payload, self.secret_key, algorithm=self.algorithm)

    def verify(self, state: str) -> str:
        try:
            payload = jwt.decode(state, self.secret_key, algorithms=[self.algorithm])
        except jwt.PyJWTError as e:
            raise CalendarSignInExpiredException(f"Sign-in state refused: {e}")
        if payload.get("purpose") != PURPOSE or not payload.get("sub"):
            raise CalendarSignInExpiredException("Sign-in state was not issued for a calendar sign-in.")
        return str(payload["sub"])
