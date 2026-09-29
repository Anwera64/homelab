"""
`GET` and `PUT /users/me/tool-approvals`: which writes agents may do for you without asking.

Every write action is listed, asking by default. Removing an event and replacing a note always ask:
the `PUT` refuses them with `always_asks`. Each member's settings are their own.
"""

import httpx
import pytest

from tests.auth_helpers import add_signed_in_member, register_admin

URL = "/api/v1/users/me/tool-approvals"


def _auto(listed: list[dict]) -> list[tuple[str, str]]:
    return [(row["tool"], row["action"]) for row in listed if row["auto"]]


@pytest.mark.asyncio
async def test_GIVEN_a_new_member_WHEN_listed_THEN_every_write_asks(client: httpx.AsyncClient):
    token, _ = await register_admin(client)

    response = await client.get(URL, headers={"Authorization": f"Bearer {token}"})

    assert response.status_code == 200
    assert response.json() == [
        {"tool": "calendar_write", "action": "create", "auto": False, "always_asks": False},
        {"tool": "calendar_write", "action": "update", "auto": False, "always_asks": False},
        {"tool": "calendar_write", "action": "delete", "auto": False, "always_asks": True},
        {"tool": "document_writer", "action": "create", "auto": False, "always_asks": False},
        {"tool": "document_writer", "action": "append", "auto": False, "always_asks": False},
        {"tool": "document_writer", "action": "replace", "auto": False, "always_asks": True},
    ]


@pytest.mark.asyncio
async def test_GIVEN_adding_events_WHEN_turned_automatic_and_undone_THEN_it_is_saved_each_time(client: httpx.AsyncClient):
    token, _ = await register_admin(client)
    headers = {"Authorization": f"Bearer {token}"}

    ticked = await client.put(URL, json={"tool": "calendar_write", "action": "create", "auto": True}, headers=headers)
    assert ticked.status_code == 200
    assert _auto(ticked.json()) == [("calendar_write", "create")]
    assert _auto((await client.get(URL, headers=headers)).json()) == [("calendar_write", "create")]

    undone = await client.put(URL, json={"tool": "calendar_write", "action": "create", "auto": False}, headers=headers)
    assert _auto(undone.json()) == []
    assert _auto((await client.get(URL, headers=headers)).json()) == []


@pytest.mark.asyncio
@pytest.mark.parametrize("tool, action", [("calendar_write", "delete"), ("document_writer", "replace")])
async def test_GIVEN_an_always_asks_action_WHEN_turned_automatic_THEN_400_always_asks(client: httpx.AsyncClient, tool, action):
    token, _ = await register_admin(client)
    headers = {"Authorization": f"Bearer {token}"}

    response = await client.put(URL, json={"tool": tool, "action": action, "auto": True}, headers=headers)

    assert response.status_code == 400
    assert response.json()["code"] == "always_asks"
    assert _auto((await client.get(URL, headers=headers)).json()) == []


@pytest.mark.asyncio
async def test_GIVEN_an_action_no_tool_has_WHEN_set_THEN_400(client: httpx.AsyncClient):
    token, _ = await register_admin(client)

    response = await client.put(
        URL, json={"tool": "calendar_read", "action": "read", "auto": True}, headers={"Authorization": f"Bearer {token}"}
    )

    assert response.status_code == 400


@pytest.mark.asyncio
async def test_GIVEN_one_member_turns_adding_automatic_WHEN_another_lists_THEN_theirs_still_asks(client: httpx.AsyncClient):
    admin_token, _ = await register_admin(client)
    member_token, _ = await add_signed_in_member(client, admin_token)

    await client.put(
        URL,
        json={"tool": "calendar_write", "action": "create", "auto": True},
        headers={"Authorization": f"Bearer {admin_token}"},
    )

    listed = await client.get(URL, headers={"Authorization": f"Bearer {member_token}"})
    assert _auto(listed.json()) == []


@pytest.mark.asyncio
async def test_GIVEN_no_sign_in_WHEN_listed_THEN_401(client: httpx.AsyncClient):
    assert (await client.get(URL)).status_code == 401
