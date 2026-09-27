from datetime import timedelta

import jwt
import pytest

from app.data.security.jwt_sign_in_state_service import JwtSignInStateService
from app.domain.exceptions import CalendarSignInExpiredException

SECRET = "test-secret-key-that-is-long-enough-for-hs256"


def test_a_state_names_the_member_who_started_the_sign_in():
    """GIVEN a state issued for a member WHEN it comes back THEN it names that member."""
    service = JwtSignInStateService(secret_key=SECRET)

    state = service.issue("member-1")

    assert service.verify(state) == "member-1"


def test_a_state_past_its_lifetime_is_refused():
    """GIVEN a state that has run out WHEN it comes back THEN the sign-in is treated as expired."""
    service = JwtSignInStateService(secret_key=SECRET, lifetime=timedelta(seconds=-1))
    state = service.issue("member-1")

    with pytest.raises(CalendarSignInExpiredException):
        service.verify(state)


def test_a_state_signed_with_another_key_is_refused():
    """GIVEN a state signed elsewhere WHEN it comes back THEN it is refused."""
    state = JwtSignInStateService(secret_key="another-secret-key-that-is-long-enough").issue("member-1")

    with pytest.raises(CalendarSignInExpiredException):
        JwtSignInStateService(secret_key=SECRET).verify(state)


def test_a_member_session_token_is_not_a_sign_in_state():
    """GIVEN a token signed with the hub key for another purpose WHEN it comes back as a state THEN it is refused."""
    session_token = jwt.encode({"sub": "member-1", "ver": 0}, SECRET, algorithm="HS256")

    with pytest.raises(CalendarSignInExpiredException):
        JwtSignInStateService(secret_key=SECRET).verify(session_token)


def test_garbage_is_not_a_sign_in_state():
    """GIVEN anything that is not a token WHEN it comes back as a state THEN it is refused."""
    with pytest.raises(CalendarSignInExpiredException):
        JwtSignInStateService(secret_key=SECRET).verify("not-a-token")
