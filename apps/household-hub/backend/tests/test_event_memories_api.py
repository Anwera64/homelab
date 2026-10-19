"""An event memory follows its event when the phone moves or removes it (memory spec §7, §10 "Event changed in the hub")."""

from datetime import datetime, timedelta, timezone
from unittest.mock import AsyncMock, patch

import httpx
import pytest
from sqlalchemy import select

from app.data.models.memory_model import MemoryModel
from app.domain.entities.calendar_event import CalendarEvent
from tests.test_memories import setup_environment

CONNECTOR = "app.data.connectors.caldav_calendar_connector.CalDavCalendarConnector"
SAT = datetime(2026, 10, 18, 18, 0, tzinfo=timezone.utc)
SUN = datetime(2026, 10, 19, 17, 30, tzinfo=timezone.utc)


def _event(start):
    return CalendarEvent(id="ev1", title="Dinner together", start_time=start, end_time=start + timedelta(hours=2))


async def _linked_memory(client, db_session, token, agent_id, session_id):
    headers = {"Authorization": f"Bearer {token}"}
    with patch(f"{CONNECTOR}.test_connection", return_value=True):
        connected = await client.post(
            "/api/v1/integrations/calendars",
            headers=headers,
            json={"provider": "apple_icloud", "url": "https://caldav.icloud.com", "username": "m@icloud.com",
                  "password": "app-password"},
        )
    assert connected.status_code in (200, 201)
    created = await client.post(
        "/api/v1/memories",
        json={"agent_id": agent_id, "scope": "household", "content": "Dinner together on Saturday at 20:00",
              "source_session_id": session_id},
        headers=headers,
    )
    memory_id = created.json()["id"]
    model = (await db_session.execute(select(MemoryModel).where(MemoryModel.id == memory_id))).scalars().one()
    model.event_id, model.event_starts_at, model.expires_at = "ev1", SAT, SAT + timedelta(hours=2)
    await db_session.commit()
    return memory_id, headers


@pytest.mark.asyncio
async def test_GIVEN_a_linked_memory_WHEN_the_phone_moves_its_event_THEN_the_page_shows_the_title_its_start_and_where_it_was(
    client: httpx.AsyncClient, db_session
):
    _, token, agent_id, session_id = await setup_environment(client)
    memory_id, headers = await _linked_memory(client, db_session, token, agent_id, session_id)

    with patch(f"{CONNECTOR}.update_event", new=AsyncMock(return_value=_event(SUN))), \
            patch(f"{CONNECTOR}.get_event", new=AsyncMock(return_value=_event(SUN))):
        moved = await client.put(
            "/api/v1/integrations/calendars/events/ev1", headers=headers,
            json={"start_time": SUN.isoformat(), "end_time": (SUN + timedelta(hours=2)).isoformat()},
        )
    assert moved.status_code == 200

    page = (await client.get("/api/v1/memories/page", headers=headers)).json()
    [row] = [m for m in page["household"] if m["id"] == memory_id]
    assert row["content"] == "Dinner together"
    assert row["time_from_event"] == "timed"
    assert datetime.fromisoformat(row["event_starts_at"].replace("Z", "+00:00")).replace(tzinfo=timezone.utc) == SUN
    assert datetime.fromisoformat(row["moved_from"].replace("Z", "+00:00")).replace(tzinfo=timezone.utc) == SAT
    assert page["to_check"] == 0


@pytest.mark.asyncio
async def test_GIVEN_a_linked_memory_WHEN_the_phone_removes_its_event_THEN_the_memory_leaves_the_page(
    client: httpx.AsyncClient, db_session
):
    _, token, agent_id, session_id = await setup_environment(client)
    memory_id, headers = await _linked_memory(client, db_session, token, agent_id, session_id)

    with patch(f"{CONNECTOR}.delete_event", new=AsyncMock(return_value=True)), \
            patch(f"{CONNECTOR}.get_event", new=AsyncMock(return_value=None)):
        removed = await client.delete("/api/v1/integrations/calendars/events/ev1", headers=headers)
    assert removed.status_code == 204

    page = (await client.get("/api/v1/memories/page", headers=headers)).json()
    assert memory_id not in [m["id"] for m in page["household"] + page["yours"]]
