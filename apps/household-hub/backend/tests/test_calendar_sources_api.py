from datetime import datetime, timedelta, timezone
from unittest.mock import AsyncMock, patch

import httpx
import pytest

from app.domain.entities.calendar_event import CalendarEvent
from app.domain.entities.calendar_source import DiscoveredCalendar
from tests.test_integrations import create_authenticated_user

CONNECTOR = "app.data.connectors.caldav_calendar_connector.CalDavCalendarConnector"
ACCOUNT = [
    DiscoveredCalendar(url="https://caldav.example.org/home/", name="Home", writable=True),
    DiscoveredCalendar(url="https://caldav.example.org/work/", name="Work", writable=True),
    DiscoveredCalendar(url="https://caldav.example.org/holidays/", name="Holidays", writable=False),
]


async def _connected(client: httpx.AsyncClient) -> dict:
    headers = {"Authorization": f"Bearer {await create_authenticated_user(client)}"}
    with patch(f"{CONNECTOR}.test_connection", return_value=True):
        connected = await client.post(
            "/api/v1/integrations/calendars",
            headers=headers,
            json={
                "provider": "apple_icloud", "url": "https://caldav.icloud.com",
                "username": "member@example.org", "password": "app-specific-pwd",
            },
        )
    assert connected.status_code == 201
    return headers


@pytest.mark.asyncio
async def test_a_member_sees_every_calendar_of_their_account_with_what_is_read(client: httpx.AsyncClient):
    headers = await _connected(client)

    with patch(f"{CONNECTOR}.list_calendars", AsyncMock(return_value=ACCOUNT)):
        listed = await client.get("/api/v1/integrations/calendars/me/sources", headers=headers)

    assert listed.status_code == 200
    assert listed.json() == [
        {"key": "c1", "name": "Home", "writable": True, "picked": True},
        {"key": "c2", "name": "Work", "writable": True, "picked": True},
        {"key": "c3", "name": "Holidays", "writable": False, "picked": False},
    ]


@pytest.mark.asyncio
async def test_a_member_picks_which_calendars_are_read_and_the_choice_is_kept(client: httpx.AsyncClient):
    headers = await _connected(client)

    with patch(f"{CONNECTOR}.list_calendars", AsyncMock(return_value=ACCOUNT)):
        picked = await client.put(
            "/api/v1/integrations/calendars/me/sources", headers=headers, json={"picked": ["c1", "c3"]}
        )
        listed = await client.get("/api/v1/integrations/calendars/me/sources", headers=headers)

    assert picked.status_code == 200
    assert {c["name"]: c["picked"] for c in listed.json()} == {"Home": True, "Work": False, "Holidays": True}


@pytest.mark.asyncio
async def test_picking_a_calendar_the_account_does_not_have_is_refused(client: httpx.AsyncClient):
    headers = await _connected(client)

    with patch(f"{CONNECTOR}.list_calendars", AsyncMock(return_value=ACCOUNT)):
        picked = await client.put(
            "/api/v1/integrations/calendars/me/sources", headers=headers, json={"picked": ["c9"]}
        )

    assert picked.status_code == 400


@pytest.mark.asyncio
async def test_a_member_without_a_calendar_has_no_calendars_to_list(client: httpx.AsyncClient):
    headers = {"Authorization": f"Bearer {await create_authenticated_user(client)}"}

    listed = await client.get("/api/v1/integrations/calendars/me/sources", headers=headers)

    assert listed.status_code == 404


@pytest.mark.asyncio
async def test_the_events_endpoint_reads_every_picked_calendar_and_names_each(client: httpx.AsyncClient):
    headers = await _connected(client)
    start = datetime(2026, 10, 22, 9, tzinfo=timezone.utc)

    async def events_on(*args, calendar_url=None, **kwargs):
        title = {"https://caldav.example.org/work/": "Dentist"}.get(calendar_url)
        return [CalendarEvent(id="e1", title=title, start_time=start, end_time=start + timedelta(hours=1))] if title else []

    with patch(f"{CONNECTOR}.list_calendars", AsyncMock(return_value=ACCOUNT)), \
            patch(f"{CONNECTOR}.fetch_events", side_effect=events_on):
        read = await client.get(
            "/api/v1/integrations/calendars/events", headers=headers,
            params={"start_time": "2026-10-22T00:00:00Z", "end_time": "2026-10-23T00:00:00Z"},
        )

    assert read.status_code == 200
    [event] = read.json()
    assert (event["title"], event["calendar"], event["calendar_name"], event["read_only"]) == ("Dentist", "c2", "Work", False)


@pytest.mark.asyncio
async def test_the_phone_adds_changes_and_removes_events_on_the_calendar_it_names(client: httpx.AsyncClient):
    headers = await _connected(client)
    start = datetime(2026, 10, 24, 19, tzinfo=timezone.utc)
    event = CalendarEvent(id="e1", title="Dinner", start_time=start, end_time=start + timedelta(hours=2))
    created_on, updated_on, deleted_on = AsyncMock(return_value=event), AsyncMock(return_value=event), AsyncMock(return_value=True)

    with patch(f"{CONNECTOR}.list_calendars", AsyncMock(return_value=ACCOUNT)), \
            patch(f"{CONNECTOR}.create_event", created_on), \
            patch(f"{CONNECTOR}.update_event", updated_on), \
            patch(f"{CONNECTOR}.delete_event", deleted_on):
        created = await client.post(
            "/api/v1/integrations/calendars/events", headers=headers,
            json={"title": "Dinner", "start_time": start.isoformat(),
                  "end_time": (start + timedelta(hours=2)).isoformat(), "calendar": "c2"},
        )
        updated = await client.put(
            "/api/v1/integrations/calendars/events/e1", headers=headers, params={"calendar": "c1"},
            json={"title": "Dinner out"},
        )
        deleted = await client.delete(
            "/api/v1/integrations/calendars/events/e1", headers=headers, params={"calendar": "c2"}
        )

    assert (created.status_code, updated.status_code, deleted.status_code) == (201, 200, 204)
    assert created.json()["calendar"] == "c2"
    assert created_on.await_args.kwargs["calendar_url"] == "https://caldav.example.org/work/"
    assert updated_on.await_args.kwargs["calendar_url"] == "https://caldav.example.org/home/"
    assert deleted_on.await_args.kwargs["calendar_url"] == "https://caldav.example.org/work/"
