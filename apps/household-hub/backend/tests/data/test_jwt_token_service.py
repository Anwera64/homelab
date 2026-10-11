from app.data.security.jwt_token_service import JwtTokenService


def _service() -> JwtTokenService:
    return JwtTokenService(secret_key="a-very-long-secret-key-for-jwt-testing-32chars", algorithm="HS256")


def test_GIVEN_a_device_session_WHEN_a_token_is_made_for_it_THEN_the_token_names_it():
    service = _service()

    token = service.create_access_token(subject="user-123", is_admin=False, token_version=0, session_id="device-1")

    assert service.decode_token(token)["sid"] == "device-1"


def test_GIVEN_no_device_session_WHEN_a_token_is_made_THEN_it_names_none():
    """What every token looked like before sign-outs reached the hub (#94)."""
    service = _service()

    token = service.create_access_token(subject="user-123", is_admin=False, token_version=0)

    assert "sid" not in service.decode_token(token)
