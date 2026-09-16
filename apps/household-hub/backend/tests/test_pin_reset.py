"""
PIN recovery without email: the other member vouches for you. They approve with their own PIN and
read out a code; you redeem it and choose a new PIN. Whoever can reach the hub itself is the
backstop, through the command in `app/cli.py`.
"""
import pytest
import httpx

from tests.auth_helpers import ADMIN_PIN, MEMBER_PIN, add_member, add_signed_in_member, deactivate, register_admin

NEW_PIN = "864209"
WRONG_PIN = "000000"


def _bearer(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


async def _approve(client: httpx.AsyncClient, token: str, target_id: str, pin: str = MEMBER_PIN) -> httpx.Response:
    return await client.post(f"/api/v1/users/{target_id}/pin-resets", json={"pin": pin}, headers=_bearer(token))


async def _redeem(client: httpx.AsyncClient, code: str, pin: str = NEW_PIN) -> httpx.Response:
    return await client.post(f"/api/v1/auth/pin-resets/{code}/redeem", json={"pin": pin})


async def _household(client: httpx.AsyncClient) -> tuple[str, str, str, str]:
    """Emma the admin and Liam the member. Returns (emma_token, emma_id, liam_token, liam_id)."""
    emma_token, emma_id = await register_admin(client, full_name="Emma")
    liam_token, liam_id = await add_signed_in_member(client, emma_token, full_name="Liam")
    return emma_token, emma_id, liam_token, liam_id


@pytest.mark.asyncio
async def test_a_member_approves_a_reset_with_their_own_pin(client: httpx.AsyncClient):
    _, emma_id, liam_token, _ = await _household(client)

    resp = await _approve(client, liam_token, emma_id)

    assert resp.status_code == 201, resp.text
    assert len(resp.json()["code"]) == 6
    assert resp.json()["expires_at"]


@pytest.mark.asyncio
async def test_a_wrong_approver_pin_is_403_and_counts_toward_their_own_lockout(client: httpx.AsyncClient):
    _, emma_id, liam_token, liam_id = await _household(client)

    refused = await _approve(client, liam_token, emma_id, pin=WRONG_PIN)

    assert refused.status_code == 403
    assert refused.json()["code"] == "wrong_pin"
    assert refused.json()["attempts_left"] == 4
    signing_in = await client.post("/api/v1/auth/login", json={"user_id": liam_id, "pin": WRONG_PIN})
    assert signing_in.json()["attempts_left"] == 3


@pytest.mark.asyncio
async def test_five_wrong_approver_pins_lock_like_sign_in(client: httpx.AsyncClient):
    _, emma_id, liam_token, _ = await _household(client)

    for _ in range(4):
        assert (await _approve(client, liam_token, emma_id, pin=WRONG_PIN)).status_code == 403
    locked = await _approve(client, liam_token, emma_id, pin=WRONG_PIN)

    assert locked.status_code == 429
    assert locked.json()["retry_after_seconds"] == 30


@pytest.mark.asyncio
async def test_nobody_approves_their_own_reset(client: httpx.AsyncClient):
    emma_token, emma_id, _, _ = await _household(client)

    resp = await _approve(client, emma_token, emma_id, pin=ADMIN_PIN)

    assert resp.status_code == 400
    assert resp.json()["code"] == "own_pin_reset"


@pytest.mark.asyncio
async def test_a_member_who_left_cannot_be_reset(client: httpx.AsyncClient):
    emma_token, _, liam_token, liam_id = await _household(client)
    await deactivate(liam_id)

    resp = await _approve(client, emma_token, liam_id, pin=ADMIN_PIN)

    assert resp.status_code == 404


@pytest.mark.asyncio
async def test_redeeming_sets_the_new_pin_and_signs_the_member_in(client: httpx.AsyncClient):
    _, emma_id, liam_token, _ = await _household(client)
    code = (await _approve(client, liam_token, emma_id)).json()["code"]

    resp = await _redeem(client, code)

    assert resp.status_code == 200, resp.text
    assert resp.json()["user"]["id"] == emma_id
    assert (await client.get("/api/v1/auth/me", headers=_bearer(resp.json()["access_token"]))).status_code == 200
    assert (await client.post("/api/v1/auth/login", json={"user_id": emma_id, "pin": NEW_PIN})).status_code == 200
    assert (await client.post("/api/v1/auth/login", json={"user_id": emma_id, "pin": ADMIN_PIN})).status_code == 401


@pytest.mark.asyncio
async def test_redeeming_signs_out_the_members_other_devices(client: httpx.AsyncClient):
    emma_phone, emma_id, liam_token, _ = await _household(client)
    code = (await _approve(client, liam_token, emma_id)).json()["code"]

    await _redeem(client, code)

    assert (await client.get("/api/v1/auth/me", headers=_bearer(emma_phone))).status_code == 401


@pytest.mark.asyncio
async def test_redeeming_clears_a_lockout_from_all_that_guessing(client: httpx.AsyncClient):
    _, emma_id, liam_token, _ = await _household(client)
    for _ in range(5):
        await client.post("/api/v1/auth/login", json={"user_id": emma_id, "pin": WRONG_PIN})
    code = (await _approve(client, liam_token, emma_id)).json()["code"]

    await _redeem(client, code)

    assert (await client.post("/api/v1/auth/login", json={"user_id": emma_id, "pin": NEW_PIN})).status_code == 200


@pytest.mark.asyncio
async def test_a_reset_code_works_once(client: httpx.AsyncClient):
    _, emma_id, liam_token, _ = await _household(client)
    code = (await _approve(client, liam_token, emma_id)).json()["code"]
    await _redeem(client, code)

    again = await _redeem(client, code, pin="111111")

    assert again.status_code == 400
    assert again.json()["code"] == "invite_invalid"


@pytest.mark.asyncio
async def test_a_new_approval_retires_the_code_before_it(client: httpx.AsyncClient):
    _, emma_id, liam_token, _ = await _household(client)
    old = (await _approve(client, liam_token, emma_id)).json()["code"]
    new = (await _approve(client, liam_token, emma_id)).json()["code"]

    assert (await _redeem(client, old)).status_code == 400
    assert (await _redeem(client, new)).status_code == 200


@pytest.mark.asyncio
async def test_reset_codes_share_the_guard_on_guessing(client: httpx.AsyncClient):
    _, emma_id, liam_token, _ = await _household(client)
    code = (await _approve(client, liam_token, emma_id)).json()["code"]

    for _ in range(4):
        assert (await _redeem(client, "ZZZZZZ")).status_code == 400
    locked = await _redeem(client, "ZZZZZZ")

    assert locked.status_code == 429
    assert locked.json()["code"] == "code_guesses_locked"
    assert (await _redeem(client, code)).status_code == 429


@pytest.mark.asyncio
@pytest.mark.parametrize("pin", ["12345", "1234567", "12a456"])
async def test_the_new_pin_is_six_digits(client: httpx.AsyncClient, pin: str):
    _, emma_id, liam_token, _ = await _household(client)
    code = (await _approve(client, liam_token, emma_id)).json()["code"]

    assert (await _redeem(client, code, pin=pin)).status_code == 422


@pytest.mark.asyncio
async def test_approving_needs_a_signed_in_member(client: httpx.AsyncClient):
    _, emma_id, _, _ = await _household(client)

    resp = await client.post(f"/api/v1/users/{emma_id}/pin-resets", json={"pin": MEMBER_PIN})

    assert resp.status_code == 401
