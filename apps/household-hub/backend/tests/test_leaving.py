"""
Leaving the household yourself. Friction matches damage: removing someone else means typing their
name, and leaving means your own PIN. The only admin can't leave, because nothing can promote anyone.
"""
import pytest
import httpx

from tests.auth_helpers import ADMIN_PIN, MEMBER_PIN, add_signed_in_member, register_admin

WRONG_PIN = "000000"


def _bearer(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


async def _leave(client: httpx.AsyncClient, token: str, pin: str) -> httpx.Response:
    return await client.request("DELETE", "/api/v1/users/me", json={"pin": pin}, headers=_bearer(token))


@pytest.mark.asyncio
async def test_a_member_leaves_with_their_pin(client: httpx.AsyncClient):
    emma_token, _ = await register_admin(client, full_name="Emma")
    liam_token, liam_id = await add_signed_in_member(client, emma_token, full_name="Liam")

    resp = await _leave(client, liam_token, MEMBER_PIN)

    assert resp.status_code == 200, resp.text
    assert (await client.get("/api/v1/auth/me", headers=_bearer(liam_token))).status_code == 401
    assert [m["full_name"] for m in (await client.get("/api/v1/auth/members")).json()] == ["Emma"]
    assert (await client.post("/api/v1/auth/login", json={"user_id": liam_id, "pin": MEMBER_PIN})).status_code == 401


@pytest.mark.asyncio
async def test_leaving_with_a_wrong_pin_is_403_and_changes_nothing(client: httpx.AsyncClient):
    emma_token, _ = await register_admin(client, full_name="Emma")
    liam_token, _ = await add_signed_in_member(client, emma_token, full_name="Liam")

    resp = await _leave(client, liam_token, WRONG_PIN)

    assert resp.status_code == 403
    assert resp.json()["code"] == "wrong_pin"
    assert resp.json()["attempts_left"] == 4
    assert (await client.get("/api/v1/auth/me", headers=_bearer(liam_token))).status_code == 200


@pytest.mark.asyncio
async def test_the_only_admin_cannot_leave(client: httpx.AsyncClient):
    emma_token, _ = await register_admin(client, full_name="Emma")
    await add_signed_in_member(client, emma_token, full_name="Liam")

    resp = await _leave(client, emma_token, ADMIN_PIN)

    assert resp.status_code == 409
    assert resp.json()["code"] == "sole_admin"
    assert "promote" not in resp.json()["detail"].lower()


@pytest.mark.asyncio
async def test_a_leaving_members_agents_pass_to_the_admin(client: httpx.AsyncClient):
    emma_token, emma_id = await register_admin(client, full_name="Emma")
    liam_token, _ = await add_signed_in_member(client, emma_token, full_name="Liam")
    agent = await client.post(
        "/api/v1/agents",
        json={"slug": "liam_bot", "name": "Liam Bot", "system_prompt": "I am a bot"},
        headers=_bearer(liam_token),
    )

    await _leave(client, liam_token, MEMBER_PIN)

    passed_on = await client.get(f"/api/v1/agents/{agent.json()['id']}", headers=_bearer(emma_token))
    assert passed_on.json()["owner_id"] == emma_id


@pytest.mark.asyncio
async def test_a_leaving_admins_agents_pass_to_the_other_admin(client: httpx.AsyncClient):
    first_token, _ = await register_admin(client, full_name="Emma")
    second_token, second_id = await add_signed_in_member(client, first_token, full_name="Noor", is_admin=True)
    agent = await client.post(
        "/api/v1/agents",
        json={"slug": "emma_bot", "name": "Emma Bot", "system_prompt": "I am a bot"},
        headers=_bearer(first_token),
    )

    resp = await _leave(client, first_token, ADMIN_PIN)

    assert resp.status_code == 200, resp.text
    passed_on = await client.get(f"/api/v1/agents/{agent.json()['id']}", headers=_bearer(second_token))
    assert passed_on.json()["owner_id"] == second_id


@pytest.mark.asyncio
async def test_leaving_needs_a_signed_in_member(client: httpx.AsyncClient):
    await register_admin(client)

    resp = await client.request("DELETE", "/api/v1/users/me", json={"pin": ADMIN_PIN})

    assert resp.status_code == 401
