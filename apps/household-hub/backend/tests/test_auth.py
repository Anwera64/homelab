import asyncio
import pytest
import httpx
import jwt
from datetime import timedelta
from unittest.mock import patch
from app.core.config import settings
from app.core.security import verify_password
from app.data.security.jwt_token_service import JwtTokenService

from tests.auth_helpers import (
    ADMIN_PIN,
    MEMBER_PIN,
    add_member,
    add_signed_in_member,
    bump_token_version,
    deactivate,
    register_admin,
    sign_in,
)

WRONG_PIN = "000000"


@pytest.mark.asyncio
async def test_auth_status_uninitialized(client: httpx.AsyncClient):
    """When no users exist, system status reports is_initialized=False."""
    response = await client.get("/api/v1/auth/status")
    assert response.status_code == 200
    data = response.json()
    assert data["is_initialized"] is False
    assert data["member_count"] == 0


@pytest.mark.asyncio
async def test_first_run_registers_the_admin_with_a_name_and_pin(client: httpx.AsyncClient):
    """First run takes a name, a PIN and a colour. No username, email or password."""
    response = await client.post(
        "/api/v1/auth/register-initial",
        json={"full_name": "Emma Larsson", "pin": ADMIN_PIN, "avatar_color": "#C05638"},
    )
    assert response.status_code == 201
    data = response.json()
    assert "access_token" in data
    assert data["token_type"] == "bearer"
    user = data["user"]
    assert user["full_name"] == "Emma Larsson"
    assert user["avatar_color"] == "#C05638"
    assert user["is_admin"] is True
    assert user["personal_space_id"] is not None
    assert not {"username", "email", "hashed_pin"} & user.keys()

    status_resp = await client.get("/api/v1/auth/status")
    assert status_resp.json() == {"is_initialized": True, "member_count": 1}


@pytest.mark.asyncio
async def test_first_run_defaults_the_colour_to_the_hygge_green(client: httpx.AsyncClient):
    response = await client.post("/api/v1/auth/register-initial", json={"full_name": "Emma", "pin": ADMIN_PIN})
    assert response.json()["user"]["avatar_color"] == "#3C6E4E"


@pytest.mark.asyncio
async def test_register_initial_blocked_when_already_initialized(client: httpx.AsyncClient):
    """Second call to register-initial must be rejected with 400 Bad Request."""
    await register_admin(client)

    resp = await client.post("/api/v1/auth/register-initial", json={"full_name": "Intruder", "pin": "111111"})
    assert resp.status_code == 400
    assert "already initialized" in resp.json()["detail"].lower()


@pytest.mark.asyncio
async def test_concurrent_register_initial_admin_prevents_multiple_admins(client: httpx.AsyncClient):
    """Concurrent calls to register-initial must allow only 1 admin to register."""
    resp1, resp2 = await asyncio.gather(
        client.post("/api/v1/auth/register-initial", json={"full_name": "Admin 1", "pin": "111111"}),
        client.post("/api/v1/auth/register-initial", json={"full_name": "Admin 2", "pin": "222222"}),
    )

    statuses = [resp1.status_code, resp2.status_code]
    assert 201 in statuses, "At least one initial admin registration must succeed"
    assert 400 in statuses, "The concurrent registration must be rejected with 400"

    status_resp = await client.get("/api/v1/auth/status")
    assert status_resp.json() == {"is_initialized": True, "member_count": 1}


@pytest.mark.asyncio
@pytest.mark.parametrize("pin", ["12345", "1234567", "12a456", "", "12 456"])
async def test_a_pin_is_exactly_six_digits(client: httpx.AsyncClient, pin: str):
    resp = await client.post("/api/v1/auth/register-initial", json={"full_name": "Emma", "pin": pin})
    assert resp.status_code == 422


@pytest.mark.asyncio
@pytest.mark.parametrize("full_name", ["", "   ", "x" * 129])
async def test_a_name_is_between_1_and_128_characters(client: httpx.AsyncClient, full_name: str):
    resp = await client.post("/api/v1/auth/register-initial", json={"full_name": full_name, "pin": ADMIN_PIN})
    assert resp.status_code == 422


@pytest.mark.asyncio
async def test_the_profile_picker_lists_active_members_without_signing_in(client: httpx.AsyncClient):
    """Id, name and colour only, for active members, to anyone who can reach the hub."""
    token, admin_id = await register_admin(client, full_name="Emma")
    liam_id = await add_member(client, token, full_name="Liam")
    gone_id = await add_member(client, token, full_name="Gone", pin="999999")
    await deactivate(gone_id)

    resp = await client.get("/api/v1/auth/members")

    assert resp.status_code == 200
    assert resp.json() == [
        {"id": admin_id, "full_name": "Emma", "avatar_color": "#3C6E4E"},
        {"id": liam_id, "full_name": "Liam", "avatar_color": "#3C6E4E"},
    ]


@pytest.mark.asyncio
async def test_signing_in_with_the_right_pin_returns_a_token(client: httpx.AsyncClient):
    token, _ = await register_admin(client)
    member_id = await add_member(client, token, full_name="Liam")

    resp = await client.post("/api/v1/auth/login", json={"user_id": member_id, "pin": MEMBER_PIN})

    assert resp.status_code == 200
    assert resp.json()["user"]["id"] == member_id
    assert "access_token" in resp.json()


@pytest.mark.asyncio
async def test_a_wrong_pin_says_how_many_attempts_are_left(client: httpx.AsyncClient):
    _, admin_id = await register_admin(client)

    resp = await client.post("/api/v1/auth/login", json={"user_id": admin_id, "pin": WRONG_PIN})

    assert resp.status_code == 401
    assert resp.json()["attempts_left"] == 4


@pytest.mark.asyncio
async def test_the_fifth_wrong_pin_locks_the_member_for_30_seconds(client: httpx.AsyncClient):
    _, admin_id = await register_admin(client)

    misses = [await client.post("/api/v1/auth/login", json={"user_id": admin_id, "pin": WRONG_PIN}) for _ in range(5)]

    assert [m.status_code for m in misses] == [401, 401, 401, 401, 429]
    assert [m.json().get("attempts_left") for m in misses[:4]] == [4, 3, 2, 1]
    assert misses[4].json()["retry_after_seconds"] == 30
    assert misses[4].headers["Retry-After"] == "30"

    right_but_locked = await client.post("/api/v1/auth/login", json={"user_id": admin_id, "pin": ADMIN_PIN})
    assert right_but_locked.status_code == 429


@pytest.mark.asyncio
async def test_a_right_pin_resets_the_count(client: httpx.AsyncClient):
    _, admin_id = await register_admin(client)
    for _ in range(3):
        await client.post("/api/v1/auth/login", json={"user_id": admin_id, "pin": WRONG_PIN})

    await sign_in(client, admin_id, ADMIN_PIN)
    miss = await client.post("/api/v1/auth/login", json={"user_id": admin_id, "pin": WRONG_PIN})

    assert miss.json()["attempts_left"] == 4


@pytest.mark.asyncio
async def test_an_inactive_member_cannot_sign_in(client: httpx.AsyncClient):
    token, _ = await register_admin(client)
    member_id = await add_member(client, token)
    await deactivate(member_id)

    resp = await client.post("/api/v1/auth/login", json={"user_id": member_id, "pin": MEMBER_PIN})

    assert resp.status_code == 401
    assert "attempts_left" not in resp.json()


@pytest.mark.asyncio
async def test_login_timing_defense_invokes_pin_verification_for_unknown_member(client: httpx.AsyncClient):
    """
    An unknown member id is still checked against a dummy hash, so the answer takes as long as a
    real miss does.
    """
    with patch("app.core.security.verify_password", wraps=verify_password) as verify_mock:
        resp = await client.post("/api/v1/auth/login", json={"user_id": "no-such-member", "pin": "123456"})
        assert resp.status_code == 401
        verify_mock.assert_called_once()


@pytest.mark.asyncio
async def test_get_me_with_bearer_token(client: httpx.AsyncClient):
    """GET /api/v1/auth/me returns the authenticated member profile."""
    token, _ = await register_admin(client, full_name="Emma")

    resp = await client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})

    assert resp.status_code == 200
    assert resp.json()["full_name"] == "Emma"
    assert resp.json()["personal_space_id"] is not None


@pytest.mark.asyncio
async def test_a_token_carries_the_members_token_version(client: httpx.AsyncClient):
    token, _ = await register_admin(client)

    assert jwt.decode(token, options={"verify_signature": False})["ver"] == 0


@pytest.mark.asyncio
async def test_bumping_the_version_signs_out_existing_tokens(client: httpx.AsyncClient):
    """The JWT can't be revoked as issued, so a newer version on the member refuses the old one."""
    token, user_id = await register_admin(client)

    await bump_token_version(user_id)

    resp = await client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 401


def _token_for(user_id: str, token_version: int = 0, lasts: timedelta = timedelta(minutes=5)) -> str:
    """A token signed like the hub's, with a lifetime the test chooses."""
    service = JwtTokenService(secret_key=settings.SECRET_KEY, algorithm=settings.ALGORITHM)
    return service.create_access_token(subject=user_id, is_admin=True, token_version=token_version, expires_delta=lasts)


def _claims(token: str) -> dict:
    return jwt.decode(token, options={"verify_signature": False})


@pytest.mark.asyncio
async def test_refreshing_gives_a_token_that_lasts_longer_with_the_same_version(client: httpx.AsyncClient):
    _, user_id = await register_admin(client)
    old = _token_for(user_id)

    resp = await client.post("/api/v1/auth/refresh", headers={"Authorization": f"Bearer {old}"})

    assert resp.status_code == 200, resp.text
    new = resp.json()["access_token"]
    assert _claims(new)["exp"] > _claims(old)["exp"]
    assert _claims(new)["ver"] == 0
    assert resp.json()["user"]["id"] == user_id
    me = await client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {new}"})
    assert me.status_code == 200


@pytest.mark.asyncio
async def test_a_token_from_before_the_version_changed_cannot_refresh(client: httpx.AsyncClient):
    token, user_id = await register_admin(client)
    await bump_token_version(user_id)

    resp = await client.post("/api/v1/auth/refresh", headers={"Authorization": f"Bearer {token}"})

    assert resp.status_code == 401


@pytest.mark.asyncio
async def test_an_expired_token_cannot_refresh(client: httpx.AsyncClient):
    _, user_id = await register_admin(client)
    expired = _token_for(user_id, lasts=timedelta(seconds=-1))

    resp = await client.post("/api/v1/auth/refresh", headers={"Authorization": f"Bearer {expired}"})

    assert resp.status_code == 401


@pytest.mark.asyncio
async def test_an_inactive_member_cannot_refresh(client: httpx.AsyncClient):
    admin_token, _ = await register_admin(client)
    token, member_id = await add_signed_in_member(client, admin_token)
    await deactivate(member_id)

    resp = await client.post("/api/v1/auth/refresh", headers={"Authorization": f"Bearer {token}"})

    assert resp.status_code == 401


@pytest.mark.asyncio
async def test_refreshing_needs_a_token(client: httpx.AsyncClient):
    resp = await client.post("/api/v1/auth/refresh")

    assert resp.status_code == 401


@pytest.mark.asyncio
async def test_members_are_no_longer_created_through_the_users_endpoint(client: httpx.AsyncClient):
    """The admin inventing someone's credential is gone; invites replace it."""
    token, _ = await register_admin(client)

    resp = await client.post(
        "/api/v1/users",
        json={"full_name": "Liam", "pin": MEMBER_PIN},
        headers={"Authorization": f"Bearer {token}"},
    )

    assert resp.status_code == 405


@pytest.mark.asyncio
async def test_household_members_see_each_other(client: httpx.AsyncClient):
    admin_token, _ = await register_admin(client)
    await add_member(client, admin_token)

    users_list = await client.get("/api/v1/users", headers={"Authorization": f"Bearer {admin_token}"})

    assert users_list.status_code == 200
    assert len(users_list.json()) == 2


def test_production_security_validation():
    """Settings must refuse to start in production if SECRET_KEY is insecure or too short."""
    from pydantic import ValidationError
    from app.core.config import Settings

    with pytest.raises(ValidationError):
        Settings(
            ENVIRONMENT="production",
            SECRET_KEY="insecure-dev-secret-key-change-in-production-min-32chars",
        )

    with pytest.raises(ValidationError):
        Settings(
            ENVIRONMENT="production",
            SECRET_KEY="short-secret-key",
        )

    valid_prod = Settings(
        ENVIRONMENT="production",
        SECRET_KEY="a-very-strong-production-secret-key-with-over-32-characters!",
    )
    assert valid_prod.ENVIRONMENT == "production"
