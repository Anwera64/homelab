"""A member's own account: changing their PIN."""
import pytest
import httpx

from tests.auth_helpers import ADMIN_PIN, register_admin, sign_in

NEW_PIN = "864209"
WRONG_PIN = "000000"


def _bearer(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


async def _change_pin(client: httpx.AsyncClient, token: str, current_pin: str, new_pin: str) -> httpx.Response:
    return await client.post(
        "/api/v1/users/me/pin",
        json={"current_pin": current_pin, "new_pin": new_pin},
        headers=_bearer(token),
    )


@pytest.mark.asyncio
async def test_changing_the_pin_keeps_this_device_and_signs_out_the_others(client: httpx.AsyncClient):
    """You might be changing it because someone saw it, so every token issued before goes."""
    this_phone, emma_id = await register_admin(client)
    other_phone = await sign_in(client, emma_id, ADMIN_PIN)

    resp = await _change_pin(client, this_phone, ADMIN_PIN, NEW_PIN)

    assert resp.status_code == 200, resp.text
    kept = resp.json()["access_token"]
    assert (await client.get("/api/v1/auth/me", headers=_bearer(kept))).status_code == 200
    assert (await client.get("/api/v1/auth/me", headers=_bearer(other_phone))).status_code == 401
    assert (await client.get("/api/v1/auth/me", headers=_bearer(this_phone))).status_code == 401


@pytest.mark.asyncio
async def test_the_new_pin_signs_in_and_the_old_one_does_not(client: httpx.AsyncClient):
    token, emma_id = await register_admin(client)

    await _change_pin(client, token, ADMIN_PIN, NEW_PIN)

    assert (await client.post("/api/v1/auth/login", json={"user_id": emma_id, "pin": NEW_PIN})).status_code == 200
    assert (await client.post("/api/v1/auth/login", json={"user_id": emma_id, "pin": ADMIN_PIN})).status_code == 401


@pytest.mark.asyncio
async def test_a_wrong_current_pin_is_403_with_attempts_left(client: httpx.AsyncClient):
    """Not 401: the token is fine, and the phone signs out on a 401."""
    token, _ = await register_admin(client)

    resp = await _change_pin(client, token, WRONG_PIN, NEW_PIN)

    assert resp.status_code == 403
    assert resp.json()["code"] == "wrong_pin"
    assert resp.json()["attempts_left"] == 4
    assert (await client.get("/api/v1/auth/me", headers=_bearer(token))).status_code == 200


@pytest.mark.asyncio
async def test_five_wrong_current_pins_lock_like_sign_in(client: httpx.AsyncClient):
    token, _ = await register_admin(client)

    for _ in range(4):
        assert (await _change_pin(client, token, WRONG_PIN, NEW_PIN)).status_code == 403
    resp = await _change_pin(client, token, WRONG_PIN, NEW_PIN)

    assert resp.status_code == 429
    assert resp.json()["retry_after_seconds"] == 30


@pytest.mark.asyncio
@pytest.mark.parametrize("new_pin", ["12345", "1234567", "12a456"])
async def test_the_new_pin_is_six_digits(client: httpx.AsyncClient, new_pin: str):
    token, _ = await register_admin(client)

    resp = await _change_pin(client, token, ADMIN_PIN, new_pin)

    assert resp.status_code == 422


@pytest.mark.asyncio
async def test_changing_the_pin_needs_a_signed_in_member(client: httpx.AsyncClient):
    resp = await client.post("/api/v1/users/me/pin", json={"current_pin": ADMIN_PIN, "new_pin": NEW_PIN})

    assert resp.status_code == 401
