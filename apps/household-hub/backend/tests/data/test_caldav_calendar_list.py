import logging
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from app.data.connectors.caldav_calendar_connector import CalDavCalendarConnector
from app.domain.entities.calendar_source import DiscoveredCalendar
from app.domain.entities.integration_credential import CalendarCredential

HOME_SET = "https://cal.example/123/calendars/"

WRITE = "<d:privilege><d:read/></d:privilege><d:privilege><d:write/></d:privilege>"
READ_ONLY = "<d:privilege><d:read/></d:privilege><d:privilege><d:read-free-busy/></d:privilege>"


def _calendar(href: str, name: str, privileges: str = WRITE, components=("VEVENT",)) -> str:
    comps = "".join(f'<c:comp name="{c}"/>' for c in components)
    privilege_set = f"<d:current-user-privilege-set>{privileges}</d:current-user-privilege-set>" if privileges else ""
    return (
        f"<d:response><d:href>{href}</d:href><d:propstat><d:prop>"
        f"<d:displayname>{name}</d:displayname>"
        f"<d:resourcetype><d:collection/><c:calendar/></d:resourcetype>"
        f"{privilege_set}"
        f"<c:supported-calendar-component-set>{comps}</c:supported-calendar-component-set>"
        f"</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"
    )


def _home_set_itself() -> str:
    return (
        f"<d:response><d:href>/123/calendars/</d:href><d:propstat><d:prop>"
        f"<d:resourcetype><d:collection/></d:resourcetype>"
        f"</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"
    )


def _multistatus(*responses: str) -> bytes:
    return (
        '<?xml version="1.0" encoding="utf-8"?>'
        '<d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">'
        + "".join(responses)
        + "</d:multistatus>"
    ).encode()


def _password_credential() -> CalendarCredential:
    return CalendarCredential(
        id="cred-1", user_id="user-1", provider="apple_icloud", url="https://cal.example",
        username="member@example.org", encrypted_secret="enc",
    )


def _account(*calendars: str) -> MagicMock:
    client = MagicMock()
    client.principal.return_value.calendar_home_set.url = HOME_SET
    client.propfind.return_value._raw = _multistatus(_home_set_itself(), *calendars)
    return client


async def _list_with(client: MagicMock):
    connector = CalDavCalendarConnector()
    with patch.object(connector, "_sync_get_client", return_value=client):
        return await connector.list_calendars(_password_credential(), "app-password")


@pytest.mark.asyncio
async def test_every_event_calendar_of_a_password_account_is_listed_with_its_full_address():
    calendars = await _list_with(_account(
        _calendar("/123/calendars/home/", "Home"),
        _calendar("/123/calendars/work/", "Work"),
    ))

    assert calendars == [
        DiscoveredCalendar(url="https://cal.example/123/calendars/home/", name="Home", writable=True),
        DiscoveredCalendar(url="https://cal.example/123/calendars/work/", name="Work", writable=True),
    ]


@pytest.mark.asyncio
async def test_a_calendar_the_member_may_only_read_is_listed_as_read_only():
    calendars = await _list_with(_account(_calendar("/123/calendars/shared/", "Shared", privileges=READ_ONLY)))

    assert calendars[0].writable is False


@pytest.mark.asyncio
async def test_a_calendar_whose_server_says_nothing_of_privileges_is_taken_as_writable():
    calendars = await _list_with(_account(_calendar("/123/calendars/home/", "Home", privileges="")))

    assert calendars[0].writable is True


@pytest.mark.asyncio
async def test_a_reminders_list_holds_no_events_and_is_left_out():
    calendars = await _list_with(_account(
        _calendar("/123/calendars/home/", "Home"),
        _calendar("/123/calendars/tasks/", "Reminders", components=("VTODO",)),
    ))

    assert [c.name for c in calendars] == ["Home"]


@pytest.mark.asyncio
async def test_a_google_account_is_listed_by_googles_calendar_list_with_its_token():
    google = MagicMock()
    google.list = AsyncMock(return_value=[DiscoveredCalendar(url="https://g/a/events", name="A", writable=True)])
    connector = CalDavCalendarConnector(google_calendars=google)
    credential = CalendarCredential(
        id="cred-2", user_id="user-1", provider="google_caldav", url="https://g/a/events",
        username="a@example.org", encrypted_secret="enc", auth_kind="oauth",
    )

    calendars = await connector.list_calendars(credential, "access-token")

    assert [c.name for c in calendars] == ["A"]
    google.list.assert_awaited_once_with("access-token")


@pytest.mark.asyncio
async def test_events_are_read_from_the_calendar_asked_for_and_say_its_name():
    client = MagicMock()
    work = MagicMock()
    work.name = "Work"
    work.date_search.return_value = []
    client.calendar.return_value = work
    connector = CalDavCalendarConnector()
    from datetime import datetime, timezone

    with patch.object(connector, "_sync_get_client", return_value=client):
        await connector.fetch_events(
            _password_credential(), "app-password",
            datetime(2026, 10, 22, tzinfo=timezone.utc), datetime(2026, 10, 23, tzinfo=timezone.utc),
            calendar_url="https://cal.example/123/calendars/work/",
        )

    client.calendar.assert_called_once_with(url="https://cal.example/123/calendars/work/")
    client.principal.assert_not_called()


@pytest.mark.asyncio
async def test_an_event_that_cannot_be_read_is_logged_rather_than_dropped_silently(caplog):
    from datetime import datetime, timezone

    broken = MagicMock()
    broken.data = "not an icalendar"
    broken.url = "https://cal.example/123/calendars/work/broken.ics"
    work = MagicMock()
    work.name = "Work"
    work.date_search.return_value = [broken]
    client = MagicMock()
    client.calendar.return_value = work
    connector = CalDavCalendarConnector()

    with caplog.at_level(logging.WARNING), patch.object(connector, "_sync_get_client", return_value=client):
        events = await connector.fetch_events(
            _password_credential(), "app-password",
            datetime(2026, 10, 22, tzinfo=timezone.utc), datetime(2026, 10, 23, tzinfo=timezone.utc),
            calendar_url="https://cal.example/123/calendars/work/",
        )

    assert events == []
    assert "broken.ics" in caplog.text
