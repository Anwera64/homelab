import asyncio
import pytest
import httpx
from unittest.mock import patch
from app.core.security import verify_password



@pytest.mark.asyncio
async def test_auth_status_uninitialized(client: httpx.AsyncClient):
    """When no users exist, system status reports is_initialized=False."""
    response = await client.get("/api/v1/auth/status")
    assert response.status_code == 200
    data = response.json()
    assert data["is_initialized"] is False
    assert data["member_count"] == 0


@pytest.mark.asyncio
async def test_first_run_register_admin(client: httpx.AsyncClient):
    """First registered user becomes Admin and gets an auto-provisioned personal space."""
    payload = {
        "username": "admin_anwera",
        "email": "anwera@homelab.local",
        "password": "StrongPassword123!",
        "full_name": "Anwera Admin",
        "avatar_color": "#4F46E5",
    }
    response = await client.post("/api/v1/auth/register-initial", json=payload)
    assert response.status_code == 201
    data = response.json()
    assert "access_token" in data
    assert data["token_type"] == "bearer"
    user = data["user"]
    assert user["username"] == "admin_anwera"
    assert user["is_admin"] is True
    assert user["personal_space_id"] is not None

    # Status should now be initialized
    status_resp = await client.get("/api/v1/auth/status")
    assert status_resp.status_code == 200
    assert status_resp.json()["is_initialized"] is True
    assert status_resp.json()["member_count"] == 1


@pytest.mark.asyncio
async def test_register_initial_blocked_when_already_initialized(client: httpx.AsyncClient):
    """Second call to register-initial must be rejected with 400 Bad Request."""
    admin_payload = {
        "username": "admin_user",
        "email": "admin@homelab.local",
        "password": "Password123!",
        "full_name": "First Admin",
    }
    await client.post("/api/v1/auth/register-initial", json=admin_payload)

    # Attempt second registration
    second_payload = {
        "username": "intruder",
        "email": "intruder@homelab.local",
        "password": "Password123!",
        "full_name": "Intruder",
    }
    resp = await client.post("/api/v1/auth/register-initial", json=second_payload)
    assert resp.status_code == 400
    assert "already initialized" in resp.json()["detail"].lower()


@pytest.mark.asyncio
async def test_concurrent_register_initial_admin_prevents_multiple_admins(client: httpx.AsyncClient):
    """Concurrent calls to register-initial must allow only 1 admin to register."""
    payload1 = {
        "username": "concurrent_admin1",
        "email": "admin1@homelab.local",
        "password": "Password123!",
        "full_name": "Concurrent Admin 1",
    }
    payload2 = {
        "username": "concurrent_admin2",
        "email": "admin2@homelab.local",
        "password": "Password123!",
        "full_name": "Concurrent Admin 2",
    }

    resp1, resp2 = await asyncio.gather(
        client.post("/api/v1/auth/register-initial", json=payload1),
        client.post("/api/v1/auth/register-initial", json=payload2),
    )

    statuses = [resp1.status_code, resp2.status_code]
    assert 201 in statuses, "At least one initial admin registration must succeed"
    assert 400 in statuses, "The concurrent registration must be rejected with 400"
    
    # Confirm status reports exactly 1 member
    status_resp = await client.get("/api/v1/auth/status")
    assert status_resp.status_code == 200
    assert status_resp.json()["is_initialized"] is True
    assert status_resp.json()["member_count"] == 1


@pytest.mark.asyncio
async def test_login_success_and_invalid_credentials(client: httpx.AsyncClient):
    """Verify login issuing valid JWT and rejection of invalid credentials."""
    # Register admin
    await client.post(
        "/api/v1/auth/register-initial",
        json={
            "username": "anwera",
            "email": "anwera@homelab.local",
            "password": "CorrectPassword123",
            "full_name": "Anwera",
        },
    )

    # Valid login
    login_resp = await client.post(
        "/api/v1/auth/login",
        json={"username": "anwera", "password": "CorrectPassword123"},
    )
    assert login_resp.status_code == 200
    token_data = login_resp.json()
    assert "access_token" in token_data

    # Invalid password
    bad_login = await client.post(
        "/api/v1/auth/login",
        json={"username": "anwera", "password": "WrongPassword!"},
    )
    assert bad_login.status_code == 401

    # Non-existent user
    unknown_login = await client.post(
        "/api/v1/auth/login",
        json={"username": "ghost", "password": "Password123"},
    )
    assert unknown_login.status_code == 401


@pytest.mark.asyncio
async def test_get_me_with_bearer_token(client: httpx.AsyncClient):
    """GET /api/v1/auth/me returns the authenticated member profile."""
    reg = await client.post(
        "/api/v1/auth/register-initial",
        json={
            "username": "anwera",
            "email": "anwera@homelab.local",
            "password": "CorrectPassword123",
            "full_name": "Anwera",
        },
    )
    token = reg.json()["access_token"]

    resp = await client.get(
        "/api/v1/auth/me",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert resp.status_code == 200
    data = resp.json()
    assert data["username"] == "anwera"
    assert data["personal_space_id"] is not None


@pytest.mark.asyncio
async def test_admin_member_management_lifecycle(client: httpx.AsyncClient):
    """Admin can create members; members cannot create members."""
    # 1. Setup Admin
    admin_reg = await client.post(
        "/api/v1/auth/register-initial",
        json={
            "username": "admin",
            "email": "admin@homelab.local",
            "password": "AdminPassword123",
            "full_name": "Household Admin",
        },
    )
    admin_token = admin_reg.json()["access_token"]

    # 2. Admin creates regular member (User B)
    new_member_payload = {
        "username": "partner",
        "email": "partner@homelab.local",
        "password": "PartnerPassword123",
        "full_name": "Household Partner",
        "avatar_color": "#10B981",
        "is_admin": False,
    }
    create_resp = await client.post(
        "/api/v1/users",
        json=new_member_payload,
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert create_resp.status_code == 201
    member_data = create_resp.json()
    assert member_data["username"] == "partner"
    assert member_data["is_admin"] is False
    assert member_data["personal_space_id"] is not None

    # 3. Regular member logs in
    partner_login = await client.post(
        "/api/v1/auth/login",
        json={"username": "partner", "password": "PartnerPassword123"},
    )
    assert partner_login.status_code == 200
    partner_token = partner_login.json()["access_token"]

    # 4. Regular member lists users -> succeeds (household visibility)
    users_list = await client.get(
        "/api/v1/users",
        headers={"Authorization": f"Bearer {partner_token}"},
    )
    assert users_list.status_code == 200
    assert len(users_list.json()) == 2

    # 5. Regular member tries to create another member -> 403 Forbidden!
    forbidden_resp = await client.post(
        "/api/v1/users",
        json={
            "username": "guest",
            "email": "guest@homelab.local",
            "password": "GuestPassword123",
            "full_name": "Guest",
        },
        headers={"Authorization": f"Bearer {partner_token}"},
    )
    assert forbidden_resp.status_code == 403


@pytest.mark.asyncio
async def test_password_length_validation_rules(client: httpx.AsyncClient):
    """Passwords must be between 8 and 72 characters (preventing bcrypt buffer overflow and weak passwords)."""
    # 1. Too short (< 8 chars)
    short_resp = await client.post(
        "/api/v1/auth/register-initial",
        json={
            "username": "admin",
            "email": "admin@homelab.local",
            "password": "short",
            "full_name": "Admin",
        },
    )
    assert short_resp.status_code == 422

    # 2. Too long (> 72 chars)
    long_pwd = "A" * 73
    long_resp = await client.post(
        "/api/v1/auth/register-initial",
        json={
            "username": "admin",
            "email": "admin@homelab.local",
            "password": long_pwd,
            "full_name": "Admin",
        },
    )
    assert long_resp.status_code == 422


def test_production_security_validation():
    """Settings must refuse to start in production if SECRET_KEY is insecure or too short."""
    from pydantic import ValidationError
    from app.core.config import Settings

    # Insecure key in production raises ValidationError
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

    # Valid key in production succeeds
    valid_prod = Settings(
        ENVIRONMENT="production",
        SECRET_KEY="a-very-strong-production-secret-key-with-over-32-characters!",
    )
    assert valid_prod.ENVIRONMENT == "production"


@pytest.mark.asyncio
async def test_login_timing_defense_invokes_password_verification_for_unknown_user(client: httpx.AsyncClient):
    """
    Login endpoint must verify password against a dummy hash when username does not exist
    to guarantee constant-time execution and prevent user enumeration timing attacks.
    """
    with patch("app.core.security.verify_password", wraps=verify_password) as verify_mock:
        resp = await client.post(
            "/api/v1/auth/login",
            json={"username": "non_existent_user_xyz", "password": "AnyPassword123!"},
        )
        assert resp.status_code == 401
        assert resp.json()["detail"] == "Invalid username or password"
        verify_mock.assert_called_once()


