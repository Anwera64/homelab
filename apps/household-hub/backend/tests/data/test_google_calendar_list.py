import httpx
import pytest

from app.data.connectors.google_calendar_list import GoogleCalendarList
from app.domain.entities.calendar_source import DiscoveredCalendar
from app.domain.exceptions import CalendarAuthException, CalendarUnreachableException


def _list(handler) -> tuple[GoogleCalendarList, list[httpx.Request]]:
    seen: list[httpx.Request] = []

    def record(request: httpx.Request) -> httpx.Response:
        seen.append(request)
        return handler(request)

    return GoogleCalendarList(client=httpx.AsyncClient(transport=httpx.MockTransport(record))), seen


@pytest.mark.asyncio
async def test_each_calendar_on_the_list_becomes_its_caldav_address_and_whether_it_can_be_written():
    """GIVEN an account with its own, a shared and a holiday calendar WHEN listed THEN each reads by its CalDAV address and only the first two are writable."""
    google, seen = _list(lambda r: httpx.Response(200, json={"items": [
        {"id": "member@example.org", "summary": "member@example.org", "accessRole": "owner", "primary": True},
        {"id": "family123@group.calendar.google.com", "summary": "Family", "accessRole": "writer"},
        {"id": "en.usa#holiday@group.v.calendar.google.com", "summary": "Holidays", "accessRole": "reader"},
    ]}))

    calendars = await google.list("the-token")

    assert calendars == [
        DiscoveredCalendar(
            url="https://apidata.googleusercontent.com/caldav/v2/member@example.org/events",
            name="member@example.org",
            writable=True,
        ),
        DiscoveredCalendar(
            url="https://apidata.googleusercontent.com/caldav/v2/family123@group.calendar.google.com/events",
            name="Family",
            writable=True,
        ),
        DiscoveredCalendar(
            url="https://apidata.googleusercontent.com/caldav/v2/en.usa%23holiday@group.v.calendar.google.com/events",
            name="Holidays",
            writable=False,
        ),
    ]
    assert seen[0].url.path == "/calendar/v3/users/me/calendarList"
    assert seen[0].headers["Authorization"] == "Bearer the-token"


@pytest.mark.asyncio
async def test_a_calendar_the_member_renamed_for_themselves_shows_that_name():
    """GIVEN a calendar with a summaryOverride WHEN listed THEN the member's own name for it is used."""
    google, _ = _list(lambda r: httpx.Response(200, json={"items": [
        {"id": "x@group.calendar.google.com", "summary": "Team", "summaryOverride": "Work", "accessRole": "writer"},
    ]}))

    assert [c.name for c in await google.list("t")] == ["Work"]


@pytest.mark.asyncio
async def test_a_long_list_is_read_page_by_page():
    """GIVEN a list on two pages WHEN listed THEN both pages are read."""
    def google_pages(request: httpx.Request) -> httpx.Response:
        if request.url.params.get("pageToken") == "page-2":
            return httpx.Response(200, json={"items": [{"id": "b@x", "summary": "B", "accessRole": "owner"}]})
        return httpx.Response(200, json={"items": [{"id": "a@x", "summary": "A", "accessRole": "owner"}], "nextPageToken": "page-2"})

    google, seen = _list(google_pages)

    assert [c.name for c in await google.list("t")] == ["A", "B"]
    assert len(seen) == 2


@pytest.mark.asyncio
async def test_a_refused_token_says_sign_in_again():
    """GIVEN Google refuses the token WHEN listed THEN it is a refused sign-in, not a failure."""
    google, _ = _list(lambda r: httpx.Response(401))

    with pytest.raises(CalendarAuthException):
        await google.list("t")


@pytest.mark.asyncio
async def test_google_out_of_reach_says_unreachable():
    """GIVEN Google can't be reached WHEN listed THEN it says the calendar is unreachable."""
    def down(request):
        raise httpx.ConnectError("no route")

    google, _ = _list(down)

    with pytest.raises(CalendarUnreachableException):
        await google.list("t")
