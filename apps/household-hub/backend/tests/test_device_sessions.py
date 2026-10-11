"""
A member is signed in per device (#94). Each sign-in opens a device session, its id rides in that
device's tokens as `sid`, and signing out closes it — for that device and no other.
"""
import logging
from datetime import datetime, timedelta, timezone

import httpx
import jwt
import pytest
from sqlalchemy import text

from app.bootstrap.di import get_container
from app.core.config import settings
from app.data.security.jwt_token_service import JwtTokenService
from app.domain.use_cases.auth.purge_expired_device_sessions import PurgeExpiredDeviceSessionsUseCase
from tests.auth_helpers import ADMIN_PIN, MEMBER_PIN, add_member, add_signed_in_member, register_admin, sign_in
from tests.conftest import TestingSessionLocal

NEW_PIN = "864209"


def _bearer(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


def _sid(token: str) -> str | None:
    return jwt.decode(token, options={"verify_signature": False}).get("sid")


async def _devices_of(user_id: str) -> set[str]:
    """The ids of the device sessions the hub holds for that member."""
    async with TestingSessionLocal() as session:
        rows = await session.execute(
            text("SELECT id FROM device_sessions WHERE user_id = :user_id"), {"user_id": user_id}
        )
        return {row[0] for row in rows}


@pytest.mark.asyncio
async def test_GIVEN_an_empty_hub_WHEN_the_first_member_registers_THEN_their_token_names_a_device_session(
    client: httpx.AsyncClient,
):
    token, user_id = await register_admin(client)

    assert _sid(token) is not None
    assert await _devices_of(user_id) == {_sid(token)}


@pytest.mark.asyncio
async def test_GIVEN_a_member_WHEN_they_sign_in_with_their_pin_THEN_their_token_names_a_device_session(
    client: httpx.AsyncClient,
):
    admin_token, _ = await register_admin(client)
    member_id = await add_member(client, admin_token)

    token = await sign_in(client, member_id, MEMBER_PIN)

    assert _sid(token) in await _devices_of(member_id)


@pytest.mark.asyncio
async def test_GIVEN_an_invite_WHEN_it_is_redeemed_THEN_the_joiners_token_names_a_device_session(
    client: httpx.AsyncClient,
):
    admin_token, _ = await register_admin(client)
    invite = await client.post("/api/v1/invites", json={"invited_name": "Liam"}, headers=_bearer(admin_token))

    redeemed = await client.post(
        f"/api/v1/invites/{invite.json()['code']}/redeem", json={"full_name": "Liam", "pin": MEMBER_PIN}
    )

    assert redeemed.status_code == 201, redeemed.text
    token = redeemed.json()["access_token"]
    assert await _devices_of(redeemed.json()["user"]["id"]) == {_sid(token)}


@pytest.mark.asyncio
async def test_GIVEN_a_reset_code_WHEN_it_is_redeemed_THEN_the_token_names_a_device_session(
    client: httpx.AsyncClient,
):
    admin_token, admin_id = await register_admin(client)
    liam_token, _ = await add_signed_in_member(client, admin_token, full_name="Liam")
    approved = await client.post(
        f"/api/v1/users/{admin_id}/pin-resets", json={"pin": MEMBER_PIN}, headers=_bearer(liam_token)
    )

    redeemed = await client.post(f"/api/v1/auth/pin-resets/{approved.json()['code']}/redeem", json={"pin": NEW_PIN})

    assert redeemed.status_code == 200, redeemed.text
    assert _sid(redeemed.json()["access_token"]) in await _devices_of(admin_id)


@pytest.mark.asyncio
async def test_GIVEN_a_member_WHEN_they_sign_in_on_two_devices_THEN_each_has_its_own_device_session(
    client: httpx.AsyncClient,
):
    phone, user_id = await register_admin(client)

    tablet = await sign_in(client, user_id, ADMIN_PIN)

    assert _sid(phone) != _sid(tablet)
    assert await _devices_of(user_id) == {_sid(phone), _sid(tablet)}


async def _forget_device(sid: str) -> None:
    """What signing out does to the hub's record of a device."""
    async with TestingSessionLocal() as session:
        await session.execute(text("DELETE FROM device_sessions WHERE id = :sid"), {"sid": sid})
        await session.commit()


def _token_without_a_device(user_id: str) -> str:
    """A token as the hub made them before #94: signed like the hub's, naming no device."""
    service = JwtTokenService(secret_key=settings.SECRET_KEY, algorithm=settings.ALGORITHM)
    return service.create_access_token(subject=user_id, is_admin=True, token_version=0)


@pytest.mark.asyncio
async def test_GIVEN_a_device_session_that_is_gone_WHEN_its_token_is_used_THEN_it_is_refused(
    client: httpx.AsyncClient,
):
    token, _ = await register_admin(client)
    await _forget_device(_sid(token))

    resp = await client.get("/api/v1/auth/me", headers=_bearer(token))

    assert resp.status_code == 401


@pytest.mark.asyncio
async def test_GIVEN_a_token_from_before_devices_were_tracked_WHEN_it_is_used_THEN_it_is_still_accepted(
    client: httpx.AsyncClient,
):
    _, user_id = await register_admin(client)

    resp = await client.get("/api/v1/auth/me", headers=_bearer(_token_without_a_device(user_id)))

    assert resp.status_code == 200


async def _last_seen(sid: str) -> str:
    async with TestingSessionLocal() as session:
        rows = await session.execute(text("SELECT last_seen_at FROM device_sessions WHERE id = :sid"), {"sid": sid})
        return rows.scalar_one()


@pytest.mark.asyncio
async def test_GIVEN_a_signed_in_device_WHEN_it_refreshes_THEN_the_new_token_names_the_same_device(
    client: httpx.AsyncClient,
):
    token, user_id = await register_admin(client)
    seen_before = await _last_seen(_sid(token))

    resp = await client.post("/api/v1/auth/refresh", headers=_bearer(token))

    assert resp.status_code == 200, resp.text
    assert _sid(resp.json()["access_token"]) == _sid(token)
    assert await _devices_of(user_id) == {_sid(token)}
    assert await _last_seen(_sid(token)) > seen_before


@pytest.mark.asyncio
async def test_GIVEN_a_token_from_before_devices_were_tracked_WHEN_it_refreshes_THEN_the_new_token_names_a_device(
    client: httpx.AsyncClient,
):
    first, user_id = await register_admin(client)

    resp = await client.post("/api/v1/auth/refresh", headers=_bearer(_token_without_a_device(user_id)))

    assert resp.status_code == 200, resp.text
    fresh = _sid(resp.json()["access_token"])
    assert fresh is not None
    assert await _devices_of(user_id) == {_sid(first), fresh}


# ---- signing out --------------------------------------------------------------


async def _sign_out(client: httpx.AsyncClient, token: str) -> httpx.Response:
    return await client.post("/api/v1/auth/logout", headers=_bearer(token))


async def _accepted(client: httpx.AsyncClient, token: str) -> bool:
    return (await client.get("/api/v1/auth/me", headers=_bearer(token))).status_code == 200


@pytest.mark.asyncio
async def test_GIVEN_a_signed_in_device_WHEN_it_signs_out_THEN_its_token_is_refused_from_then_on(
    client: httpx.AsyncClient,
):
    token, user_id = await register_admin(client)

    resp = await _sign_out(client, token)

    assert resp.status_code == 204
    assert not await _accepted(client, token)
    assert await _devices_of(user_id) == set()


@pytest.mark.asyncio
async def test_GIVEN_a_device_that_refreshed_its_token_WHEN_it_signs_out_THEN_the_token_it_held_before_is_refused_too(
    client: httpx.AsyncClient,
):
    """A token copied off the phone before a refresh must not outlive the sign-out."""
    older, _ = await register_admin(client)
    newer = (await client.post("/api/v1/auth/refresh", headers=_bearer(older))).json()["access_token"]

    await _sign_out(client, newer)

    assert not await _accepted(client, older)
    assert not await _accepted(client, newer)


@pytest.mark.asyncio
async def test_GIVEN_a_member_on_two_devices_WHEN_one_signs_out_THEN_the_other_stays_signed_in(
    client: httpx.AsyncClient,
):
    phone, user_id = await register_admin(client)
    tablet = await sign_in(client, user_id, ADMIN_PIN)

    await _sign_out(client, phone)

    assert not await _accepted(client, phone)
    assert await _accepted(client, tablet)


@pytest.mark.asyncio
async def test_GIVEN_two_members_WHEN_one_signs_out_THEN_the_other_stays_signed_in(client: httpx.AsyncClient):
    emma, _ = await register_admin(client)
    liam, _ = await add_signed_in_member(client, emma)

    await _sign_out(client, liam)

    assert await _accepted(client, emma)


@pytest.mark.asyncio
async def test_GIVEN_a_token_from_before_devices_were_tracked_WHEN_it_signs_out_THEN_the_hub_answers_204(
    client: httpx.AsyncClient,
):
    _, user_id = await register_admin(client)

    resp = await _sign_out(client, _token_without_a_device(user_id))

    assert resp.status_code == 204


@pytest.mark.asyncio
async def test_GIVEN_nobody_signed_in_WHEN_a_sign_out_is_sent_THEN_it_is_refused(client: httpx.AsyncClient):
    resp = await client.post("/api/v1/auth/logout")

    assert resp.status_code == 401


@pytest.mark.asyncio
async def test_GIVEN_a_device_already_signed_out_WHEN_it_signs_out_again_THEN_it_is_refused(
    client: httpx.AsyncClient,
):
    token, _ = await register_admin(client)
    await _sign_out(client, token)

    assert (await _sign_out(client, token)).status_code == 401


@pytest.mark.asyncio
async def test_GIVEN_a_signed_in_device_WHEN_it_signs_out_THEN_the_hub_logs_who_and_which_device(
    client: httpx.AsyncClient, caplog: pytest.LogCaptureFixture
):
    token, user_id = await register_admin(client)

    with caplog.at_level(logging.INFO, logger="app"):
        await _sign_out(client, token)

    assert f"Signed out | member={user_id} device={_sid(token)}" in [r.getMessage() for r in caplog.records]


# ---- a PIN that changes, a member who goes -----------------------------------


@pytest.mark.asyncio
async def test_GIVEN_a_member_on_two_devices_WHEN_one_changes_the_pin_THEN_only_that_device_keeps_its_session(
    client: httpx.AsyncClient,
):
    phone, user_id = await register_admin(client)
    await sign_in(client, user_id, ADMIN_PIN)

    resp = await client.post(
        "/api/v1/users/me/pin", json={"current_pin": ADMIN_PIN, "new_pin": NEW_PIN}, headers=_bearer(phone)
    )

    assert resp.status_code == 200, resp.text
    fresh = resp.json()["access_token"]
    assert _sid(fresh) == _sid(phone)
    assert await _devices_of(user_id) == {_sid(phone)}
    assert await _accepted(client, fresh)


@pytest.mark.asyncio
async def test_GIVEN_a_token_from_before_devices_were_tracked_WHEN_it_changes_the_pin_THEN_the_new_token_names_a_device(
    client: httpx.AsyncClient,
):
    _, user_id = await register_admin(client)

    resp = await client.post(
        "/api/v1/users/me/pin",
        json={"current_pin": ADMIN_PIN, "new_pin": NEW_PIN},
        headers=_bearer(_token_without_a_device(user_id)),
    )

    assert resp.status_code == 200, resp.text
    fresh = resp.json()["access_token"]
    assert await _devices_of(user_id) == {_sid(fresh)}
    assert await _accepted(client, fresh)


@pytest.mark.asyncio
async def test_GIVEN_a_member_signed_in_on_a_device_WHEN_their_pin_is_reset_THEN_only_the_resetting_device_has_a_session(
    client: httpx.AsyncClient,
):
    _, admin_id = await register_admin(client)
    admin_token = await sign_in(client, admin_id, ADMIN_PIN)
    liam_token, _ = await add_signed_in_member(client, admin_token, full_name="Liam")
    approved = await client.post(
        f"/api/v1/users/{admin_id}/pin-resets", json={"pin": MEMBER_PIN}, headers=_bearer(liam_token)
    )

    redeemed = await client.post(f"/api/v1/auth/pin-resets/{approved.json()['code']}/redeem", json={"pin": NEW_PIN})

    assert await _devices_of(admin_id) == {_sid(redeemed.json()["access_token"])}


@pytest.mark.asyncio
async def test_GIVEN_a_signed_in_member_WHEN_an_admin_removes_them_THEN_none_of_their_devices_has_a_session(
    client: httpx.AsyncClient,
):
    admin_token, _ = await register_admin(client)
    _, liam_id = await add_signed_in_member(client, admin_token, full_name="Liam")
    assert await _devices_of(liam_id) != set()

    removed = await client.delete(f"/api/v1/users/{liam_id}", headers=_bearer(admin_token))

    assert removed.status_code < 300, removed.text
    assert await _devices_of(liam_id) == set()


@pytest.mark.asyncio
async def test_GIVEN_a_signed_in_member_WHEN_they_leave_the_household_THEN_none_of_their_devices_has_a_session(
    client: httpx.AsyncClient,
):
    admin_token, _ = await register_admin(client)
    liam_token, liam_id = await add_signed_in_member(client, admin_token, full_name="Liam")

    left = await client.request("DELETE", "/api/v1/users/me", json={"pin": MEMBER_PIN}, headers=_bearer(liam_token))

    assert left.status_code < 300, left.text
    assert await _devices_of(liam_id) == set()


# ---- devices nobody came back to ----------------------------------------------


@pytest.mark.asyncio
async def test_GIVEN_a_device_last_seen_longer_ago_than_a_token_lasts_WHEN_the_hubs_purge_runs_THEN_it_is_forgotten(
    client: httpx.AsyncClient,
):
    phone, user_id = await register_admin(client)
    tablet = await sign_in(client, user_id, ADMIN_PIN)
    long_ago = datetime.now(timezone.utc) - timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES + 1)
    async with TestingSessionLocal() as session:
        await session.execute(
            text("UPDATE device_sessions SET last_seen_at = :long_ago WHERE id = :sid"),
            {"long_ago": long_ago, "sid": _sid(phone)},
        )
        await session.commit()

    async with TestingSessionLocal() as session:
        removed = await get_container(session)[PurgeExpiredDeviceSessionsUseCase].execute()

    assert removed == 1
    assert await _devices_of(user_id) == {_sid(tablet)}
