"""Invites: an admin names who's joining and gets a one-time code; the joiner redeems it."""
import re
from datetime import datetime, timedelta, timezone

import pytest
import httpx

from tests.auth_helpers import add_signed_in_member, register_admin

CODE = re.compile(r"^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$")


def _bearer(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


async def _invite(client: httpx.AsyncClient, token: str, name: str = "Liam", is_admin: bool = False) -> httpx.Response:
    return await client.post(
        "/api/v1/invites",
        json={"invited_name": name, "is_admin": is_admin},
        headers=_bearer(token),
    )


@pytest.mark.asyncio
async def test_the_admin_gets_a_six_character_code_for_15_minutes(client: httpx.AsyncClient):
    token, _ = await register_admin(client)

    resp = await _invite(client, token)

    assert resp.status_code == 201, resp.text
    body = resp.json()
    assert CODE.match(body["code"])
    assert body["invited_name"] == "Liam"
    assert body["is_admin"] is False
    lasts = datetime.fromisoformat(body["expires_at"]) - datetime.now(timezone.utc)
    assert timedelta(minutes=14) < lasts <= timedelta(minutes=15)
    # The phone counts down from seconds, because its clock may differ from the hub's.
    assert 14 * 60 < body["expires_in_seconds"] <= 15 * 60


@pytest.mark.asyncio
async def test_an_admin_can_invite_another_admin(client: httpx.AsyncClient):
    token, _ = await register_admin(client)

    resp = await _invite(client, token, is_admin=True)

    assert resp.json()["is_admin"] is True


@pytest.mark.asyncio
async def test_a_member_cannot_invite(client: httpx.AsyncClient):
    token, _ = await register_admin(client)
    member_token, _ = await add_signed_in_member(client, token)

    resp = await _invite(client, member_token, name="Noor")

    assert resp.status_code == 403


@pytest.mark.asyncio
async def test_inviting_a_name_already_in_the_household_is_name_taken(client: httpx.AsyncClient):
    token, _ = await register_admin(client, full_name="Emma")

    resp = await _invite(client, token, name="emma")

    assert resp.status_code == 409
    assert resp.json()["code"] == "name_taken"


@pytest.mark.asyncio
@pytest.mark.parametrize("name", ["", "   ", "x" * 129])
async def test_an_invite_needs_a_name_of_1_to_128_characters(client: httpx.AsyncClient, name: str):
    token, _ = await register_admin(client)

    resp = await _invite(client, token, name=name)

    assert resp.status_code == 422
