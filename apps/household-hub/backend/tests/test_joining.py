"""
Joining with an invite code. Both endpoints are public — the joiner has no token yet — so what they
may do comes from the stored invite, and every wrong code counts toward the hub-wide guard.
"""
import pytest
import httpx

from tests.auth_helpers import add_member, expire_invite, register_admin

JOINER_PIN = "975310"


def _bearer(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


async def _code_for(client: httpx.AsyncClient, admin_token: str, name: str = "Liam", is_admin: bool = False) -> str:
    resp = await client.post(
        "/api/v1/invites",
        json={"invited_name": name, "is_admin": is_admin},
        headers=_bearer(admin_token),
    )
    assert resp.status_code == 201, resp.text
    return resp.json()["code"]


async def _join(client: httpx.AsyncClient, code: str, **overrides) -> httpx.Response:
    payload = {"full_name": "Liam", "pin": JOINER_PIN, "avatar_color": "#C05638", **overrides}
    return await client.post(f"/api/v1/invites/{code}/redeem", json=payload)


@pytest.mark.asyncio
async def test_looking_up_a_code_says_who_invited_whom(client: httpx.AsyncClient):
    token, _ = await register_admin(client, full_name="Emma")
    code = await _code_for(client, token)

    resp = await client.get(f"/api/v1/invites/{code}")

    assert resp.status_code == 200, resp.text
    assert resp.json() == {"invited_name": "Liam", "inviter_name": "Emma", "inviter_avatar_color": "#3C6E4E"}


@pytest.mark.asyncio
async def test_a_code_is_read_ignoring_case_and_spaces(client: httpx.AsyncClient):
    token, _ = await register_admin(client)
    code = await _code_for(client, token)

    resp = await client.get(f"/api/v1/invites/%20{code.lower()}%20")

    assert resp.status_code == 200


@pytest.mark.asyncio
async def test_the_joiner_picks_their_own_pin_and_is_signed_in(client: httpx.AsyncClient):
    token, _ = await register_admin(client)
    code = await _code_for(client, token)

    resp = await _join(client, code)

    assert resp.status_code == 201, resp.text
    body = resp.json()
    assert body["user"]["full_name"] == "Liam"
    assert body["user"]["avatar_color"] == "#C05638"
    assert (await client.get("/api/v1/auth/me", headers=_bearer(body["access_token"]))).status_code == 200
    signed_in = await client.post("/api/v1/auth/login", json={"user_id": body["user"]["id"], "pin": JOINER_PIN})
    assert signed_in.status_code == 200


@pytest.mark.asyncio
async def test_the_picker_lists_the_new_member(client: httpx.AsyncClient):
    token, _ = await register_admin(client, full_name="Emma")
    await _join(client, await _code_for(client, token))

    members = (await client.get("/api/v1/auth/members")).json()

    assert [m["full_name"] for m in members] == ["Emma", "Liam"]


@pytest.mark.asyncio
@pytest.mark.parametrize("invited_as_admin", [False, True])
async def test_is_admin_comes_from_the_invite_never_the_payload(client: httpx.AsyncClient, invited_as_admin: bool):
    token, _ = await register_admin(client)
    code = await _code_for(client, token, is_admin=invited_as_admin)

    resp = await _join(client, code, is_admin=not invited_as_admin)

    assert resp.json()["user"]["is_admin"] is invited_as_admin


@pytest.mark.asyncio
async def test_a_code_works_once(client: httpx.AsyncClient):
    token, _ = await register_admin(client)
    code = await _code_for(client, token)
    await _join(client, code)

    resp = await _join(client, code, full_name="Liam Again")

    assert resp.status_code == 400
    assert resp.json()["code"] == "invite_invalid"


@pytest.mark.asyncio
async def test_an_expired_code_is_invite_invalid(client: httpx.AsyncClient):
    token, _ = await register_admin(client)
    code = await _code_for(client, token)
    await expire_invite(code)

    assert (await client.get(f"/api/v1/invites/{code}")).json()["code"] == "invite_invalid"
    assert (await _join(client, code)).json()["code"] == "invite_invalid"


@pytest.mark.asyncio
async def test_unknown_used_and_expired_codes_answer_alike(client: httpx.AsyncClient):
    token, _ = await register_admin(client)
    used = await _code_for(client, token, name="Liam")
    await _join(client, used)
    expired = await _code_for(client, token, name="Noor")
    await expire_invite(expired)

    answers = [(await client.get(f"/api/v1/invites/{code}")) for code in ("ZZZZZZ", used, expired)]

    assert {(a.status_code, a.json()["detail"]) for a in answers} == {(answers[0].status_code, answers[0].json()["detail"])}


@pytest.mark.asyncio
async def test_a_new_code_for_the_same_name_retires_the_old_one(client: httpx.AsyncClient):
    token, _ = await register_admin(client)
    old = await _code_for(client, token)
    new = await _code_for(client, token)

    assert (await client.get(f"/api/v1/invites/{old}")).status_code == 400
    assert (await client.get(f"/api/v1/invites/{new}")).status_code == 200


@pytest.mark.asyncio
async def test_wrong_codes_make_everyone_wait(client: httpx.AsyncClient):
    token, _ = await register_admin(client)
    real = await _code_for(client, token)

    for _ in range(4):
        assert (await client.get("/api/v1/invites/ZZZZZZ")).status_code == 400
    locked = await client.get("/api/v1/invites/ZZZZZZ")

    assert locked.status_code == 429
    assert locked.json()["code"] == "code_guesses_locked"
    assert (await client.get(f"/api/v1/invites/{real}")).status_code == 429


@pytest.mark.asyncio
async def test_a_name_taken_while_joining_is_name_taken_and_keeps_the_code(client: httpx.AsyncClient):
    """The joiner can change the name they were invited as — and someone may have taken it meanwhile."""
    token, _ = await register_admin(client)
    code = await _code_for(client, token, name="Liam Larsson")
    await add_member(client, token, full_name="Liam")

    taken = await _join(client, code, full_name="Liam")
    joined = await _join(client, code, full_name="Liam B")

    assert taken.status_code == 409
    assert taken.json()["code"] == "name_taken"
    assert joined.status_code == 201


@pytest.mark.asyncio
@pytest.mark.parametrize("pin", ["12345", "1234567", "12a456"])
async def test_the_joiners_pin_is_six_digits(client: httpx.AsyncClient, pin: str):
    token, _ = await register_admin(client)
    code = await _code_for(client, token)

    resp = await _join(client, code, pin=pin)

    assert resp.status_code == 422
