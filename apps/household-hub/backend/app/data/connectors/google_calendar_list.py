from typing import List, Optional
from urllib.parse import quote

import httpx

from app.domain.entities.calendar_source import DiscoveredCalendar
from app.domain.exceptions import CalendarAuthException, CalendarUnreachableException

CALENDAR_LIST_ENDPOINT = "https://www.googleapis.com/calendar/v3/users/me/calendarList"
CALDAV_CALENDAR = "https://apidata.googleusercontent.com/caldav/v2/{id}/events"
WRITABLE_ROLES = {"owner", "writer"}


def caldav_url(calendar_id: str) -> str:
    """A Google calendar's CalDAV address. Ids such as holiday calendars carry a '#', which a URL can't."""
    return CALDAV_CALENDAR.format(id=quote(calendar_id, safe="@"))


class GoogleCalendarList:
    """
    The calendars of a Google account. Google's CalDAV only ever names one calendar and can't list
    them, so the list comes from the Calendar API, with the same sign-in.
    """

    def __init__(self, client: Optional[httpx.AsyncClient] = None, timeout: float = 10.0):
        self.client = client or httpx.AsyncClient(timeout=timeout)

    async def list(self, access_token: str) -> List[DiscoveredCalendar]:
        calendars: List[DiscoveredCalendar] = []
        page: Optional[str] = None
        while True:
            body = await self._page(access_token, page)
            for item in body.get("items", []):
                calendars.append(
                    DiscoveredCalendar(
                        url=caldav_url(item["id"]),
                        name=item.get("summaryOverride") or item.get("summary") or item["id"],
                        writable=item.get("accessRole") in WRITABLE_ROLES,
                    )
                )
            page = body.get("nextPageToken")
            if not page:
                return calendars

    async def _page(self, access_token: str, page: Optional[str]) -> dict:
        params = {"pageToken": page} if page else {}
        try:
            response = await self.client.get(
                CALENDAR_LIST_ENDPOINT, params=params, headers={"Authorization": f"Bearer {access_token}"}
            )
        except httpx.HTTPError as e:
            raise CalendarUnreachableException(f"Google could not be reached: {e}")
        if response.status_code in (401, 403):
            raise CalendarAuthException(f"Google refused the calendar sign-in ({response.status_code}).")
        if response.status_code != 200:
            raise CalendarUnreachableException(f"Google could not list the calendars ({response.status_code}).")
        return response.json()
