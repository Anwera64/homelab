import pytest
import httpx
from unittest.mock import patch

from tests.auth_helpers import add_signed_in_member, register_admin


async def create_user(client: httpx.AsyncClient, name: str, is_admin: bool = False, admin_token: str = None) -> tuple[str, str]:
    """Helper to register the initial admin, or add a member once there is one. Returns (token, personal_space_id)."""
    if admin_token is None:
        token, _ = await register_admin(client, full_name=name)
    else:
        token, _ = await add_signed_in_member(client, admin_token, full_name=name, is_admin=is_admin)
    me = await client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
    return token, me.json()["personal_space_id"]


@pytest.mark.asyncio
async def test_get_shared_space_and_default_bento_widgets(client: httpx.AsyncClient):
    """Authenticated user can fetch the shared household hub with default Bento widgets."""
    admin_token, _ = await create_user(client, "admin_user")

    response = await client.get(
        "/api/v1/spaces/shared",
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert response.status_code == 200
    data = response.json()
    assert data["type"] == "shared"
    assert data["owner_id"] is None
    settings = data["settings"]
    assert "widgets" in settings
    widget_types = [w["type"] for w in settings["widgets"]]
    assert "calendar" in widget_types
    assert "agent_launcher" in widget_types


@pytest.mark.asyncio
async def test_get_personal_space(client: httpx.AsyncClient):
    """Authenticated user can fetch their own personal space."""
    admin_token, admin_space_id = await create_user(client, "admin_user")

    response = await client.get(
        "/api/v1/spaces/personal",
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert response.status_code == 200
    data = response.json()
    assert data["id"] == admin_space_id
    assert data["type"] == "personal"
    assert "widgets" in data["settings"]


@pytest.mark.asyncio
async def test_strict_zero_leak_personal_space_isolation(client: httpx.AsyncClient):
    """
    Strict Zero-Leak Rule:
    1. Member B cannot access Member A's personal space.
    2. Even Household Admin CANNOT access Member B's personal space.
    """
    admin_token, admin_space_id = await create_user(client, "admin_user")
    member_token, member_space_id = await create_user(client, "member_b", admin_token=admin_token)

    # 1. Member B attempts to read Admin's personal space by ID -> 403 Forbidden!
    member_to_admin = await client.get(
        f"/api/v1/spaces/{admin_space_id}",
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert member_to_admin.status_code == 403
    assert "zero-leak" in member_to_admin.json()["detail"].lower() or "forbidden" in member_to_admin.json()["detail"].lower()

    # 2. Admin attempts to read Member B's personal space by ID -> 403 Forbidden!
    admin_to_member = await client.get(
        f"/api/v1/spaces/{member_space_id}",
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert admin_to_member.status_code == 403
    assert "zero-leak" in admin_to_member.json()["detail"].lower() or "forbidden" in admin_to_member.json()["detail"].lower()


@pytest.mark.asyncio
async def test_update_personal_and_shared_settings(client: httpx.AsyncClient):
    """Users can customize widget layout in personal space and shared space."""
    admin_token, admin_space_id = await create_user(client, "admin_user")

    # Update personal space settings
    custom_personal_widgets = {
        "layout_version": 2,
        "columns": 3,
        "widgets": [
            {"id": "w-custom-1", "type": "custom_card", "title": "My Research", "size": "large", "position": 0}
        ],
    }
    update_resp = await client.put(
        "/api/v1/spaces/personal/settings",
        json={"settings": custom_personal_widgets},
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert update_resp.status_code == 200
    assert update_resp.json()["settings"]["columns"] == 3

    # Update shared space settings
    custom_shared_widgets = {
        "layout_version": 2,
        "columns": 5,
        "widgets": [
            {"id": "w-shared-1", "type": "calendar", "title": "Family Cal", "size": "large", "position": 0}
        ],
    }
    update_shared = await client.put(
        "/api/v1/spaces/shared/settings",
        json={"settings": custom_shared_widgets},
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert update_shared.status_code == 200
    assert update_shared.json()["settings"]["columns"] == 5


@pytest.mark.asyncio
async def test_shared_space_read_is_non_locking_and_collaborative(client: httpx.AsyncClient):
    """
    1. Verify GET /api/v1/spaces/shared does not trigger a database commit when space already exists.
    2. Verify regular household members (non-admin) have collaborative access to update shared settings.
    """
    admin_token, _ = await create_user(client, "admin_user")
    member_token, _ = await create_user(client, "regular_member", admin_token=admin_token)

    # First ensure shared space exists
    first_resp = await client.get(
        "/api/v1/spaces/shared",
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert first_resp.status_code == 200

    # With space existing, GET /spaces/shared must NOT call commit
    with patch("sqlalchemy.ext.asyncio.AsyncSession.commit") as mock_commit:
        resp = await client.get(
            "/api/v1/spaces/shared",
            headers={"Authorization": f"Bearer {member_token}"},
        )
        assert resp.status_code == 200
        mock_commit.assert_not_called()

    # Collaborative access: member updates shared space settings
    update_resp = await client.put(
        "/api/v1/spaces/shared/settings",
        json={"settings": {"columns": 6, "widgets": []}},
        headers={"Authorization": f"Bearer {member_token}"},
    )
    assert update_resp.status_code == 200
    assert update_resp.json()["settings"]["columns"] == 6

