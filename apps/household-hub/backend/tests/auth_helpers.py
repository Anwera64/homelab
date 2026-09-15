"""
Signing people in for the HTTP tests.

`add_member` joins through the real flow: an admin invites the name, and the invite is redeemed
on the spot, against the same in-memory database the app uses.
"""
import uuid
from datetime import datetime, timedelta, timezone

import httpx
from sqlalchemy import text

from app.data.datasources.user_data_source import SqliteUserDataSource
from app.data.mappers.user_data_mapper import UserDataMapper
from app.data.repositories.user_repository_impl import UserRepositoryImpl
from tests.conftest import TestingSessionLocal

ADMIN_PIN = "135790"
MEMBER_PIN = "246801"


def _user_repo(session) -> UserRepositoryImpl:
    return UserRepositoryImpl(SqliteUserDataSource(session), UserDataMapper())


async def register_admin(client: httpx.AsyncClient, full_name: str = "Admin", pin: str = ADMIN_PIN) -> tuple[str, str]:
    """First run. Returns (access_token, user_id)."""
    resp = await client.post("/api/v1/auth/register-initial", json={"full_name": full_name, "pin": pin})
    assert resp.status_code == 201, resp.text
    body = resp.json()
    return body["access_token"], body["user"]["id"]


async def add_member(
    client: httpx.AsyncClient,
    admin_token: str,
    full_name: str = "Member",
    pin: str = MEMBER_PIN,
    is_admin: bool = False,
) -> str:
    """
    An admin invites, and the invite is redeemed on the spot as `full_name`. Returns their id.

    The invite itself is named uniquely rather than as `full_name`, so this doesn't collide with
    -- or retire -- a code a test already has outstanding for that same name.
    """
    invite_resp = await client.post(
        "/api/v1/invites",
        json={"invited_name": f"_setup_{uuid.uuid4().hex}", "is_admin": is_admin},
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert invite_resp.status_code == 201, invite_resp.text
    code = invite_resp.json()["code"]

    redeem_resp = await client.post(
        f"/api/v1/invites/{code}/redeem",
        json={"full_name": full_name, "pin": pin},
    )
    assert redeem_resp.status_code == 201, redeem_resp.text
    return redeem_resp.json()["user"]["id"]


async def sign_in(client: httpx.AsyncClient, user_id: str, pin: str) -> str:
    resp = await client.post("/api/v1/auth/login", json={"user_id": user_id, "pin": pin})
    assert resp.status_code == 200, resp.text
    return resp.json()["access_token"]


async def add_signed_in_member(
    client: httpx.AsyncClient,
    admin_token: str,
    full_name: str = "Member",
    pin: str = MEMBER_PIN,
    is_admin: bool = False,
) -> tuple[str, str]:
    """Returns (access_token, user_id)."""
    member_id = await add_member(client, admin_token, full_name=full_name, pin=pin, is_admin=is_admin)
    return await sign_in(client, member_id, pin), member_id


async def expire_invite(code: str) -> None:
    """Moves an invite's expiry into the past, as fifteen minutes of waiting would."""
    async with TestingSessionLocal() as session:
        await session.execute(
            text("UPDATE invites SET expires_at = :past WHERE code = :code"),
            {"past": datetime.now(timezone.utc) - timedelta(seconds=1), "code": code},
        )
        await session.commit()


async def bump_token_version(user_id: str) -> None:
    """What changing a PIN or removing a member does to the tokens already out there."""
    async with TestingSessionLocal() as session:
        repo = _user_repo(session)
        user = await repo.get_by_id(user_id)
        user.token_version += 1
        await repo.update(user)
        await session.commit()


async def deactivate(user_id: str) -> None:
    async with TestingSessionLocal() as session:
        repo = _user_repo(session)
        user = await repo.get_by_id(user_id)
        user.is_active = False
        await repo.update(user)
        await session.commit()
