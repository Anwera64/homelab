"""
The hub itself is the last resort for a forgotten PIN: whoever can reach the server can issue a
reset code for anyone. It prints a code rather than setting a PIN, so no PIN ends up in a shell
history, and the member redeems it the same way they would one read out by a housemate.
"""
import pytest
import httpx

from app.cli import issue_reset_code, main
from tests.auth_helpers import register_admin
from tests.conftest import TestingSessionLocal

NEW_PIN = "864209"


@pytest.mark.asyncio
async def test_the_command_prints_a_code_that_redeems(client: httpx.AsyncClient, capsys):
    _, emma_id = await register_admin(client, full_name="Emma")

    exit_code = await main(["reset-pin", "Emma"], session_factory=TestingSessionLocal)

    assert exit_code == 0
    printed = capsys.readouterr().out
    code = next(word for word in printed.split() if len(word) == 6 and word.isalnum() and word.isupper())
    resp = await client.post(f"/api/v1/auth/pin-resets/{code}/redeem", json={"pin": NEW_PIN})
    assert resp.status_code == 200, resp.text
    assert resp.json()["user"]["id"] == emma_id


@pytest.mark.asyncio
async def test_the_name_is_matched_however_it_is_capitalised(client: httpx.AsyncClient):
    await register_admin(client, full_name="Emma Larsson")

    code = await issue_reset_code("emma larsson", session_factory=TestingSessionLocal)

    assert (await client.post(f"/api/v1/auth/pin-resets/{code}/redeem", json={"pin": NEW_PIN})).status_code == 200


@pytest.mark.asyncio
async def test_a_name_nobody_here_has_fails(client: httpx.AsyncClient, capsys):
    await register_admin(client, full_name="Emma")

    exit_code = await main(["reset-pin", "Nobody"], session_factory=TestingSessionLocal)

    assert exit_code == 1
    assert "nobody" in capsys.readouterr().err.lower()


@pytest.mark.asyncio
async def test_a_new_code_retires_the_one_before_it(client: httpx.AsyncClient):
    await register_admin(client, full_name="Emma")

    old = await issue_reset_code("Emma", session_factory=TestingSessionLocal)
    new = await issue_reset_code("Emma", session_factory=TestingSessionLocal)

    assert (await client.post(f"/api/v1/auth/pin-resets/{old}/redeem", json={"pin": NEW_PIN})).status_code == 400
    assert (await client.post(f"/api/v1/auth/pin-resets/{new}/redeem", json={"pin": NEW_PIN})).status_code == 200
