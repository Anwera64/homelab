from datetime import timedelta

import pytest

from app.domain.entities.calendar_source import CalendarSource
from app.domain.use_cases.integrations.execute_tool import ExecuteToolUseCase
from app.domain.use_cases.integrations.list_available_tools import ListAvailableToolsUseCase
from tests.domain.test_integration_use_cases import (
    MockDocumentReader,
    MockDocumentRepository,
    MockSecretCipher,
    MockUnitOfWork,
)
from tests.domain.test_read_every_calendar import (
    HOLIDAYS,
    HOME,
    WORK,
    FakeAccount,
    FakeCredentials,
    FakeSources,
    _event,
)
from tests.domain.test_write_to_a_calendar import FakeWritableAccount


class Account(FakeAccount, FakeWritableAccount):
    """Reads like FakeAccount, writes like FakeWritableAccount."""

    def __init__(self, calendars):
        FakeAccount.__init__(self, calendars)
        self.wrote_to = []


def _tool(account, sources=None) -> ExecuteToolUseCase:
    return ExecuteToolUseCase(
        calendar_repo=FakeCredentials(),
        calendar_connector=account,
        search_connector=None,
        document_repo=MockDocumentRepository(),
        document_reader=MockDocumentReader(),
        cipher=MockSecretCipher(),
        uow=MockUnitOfWork(),
        calendar_sources=sources or FakeSources(),
    )


async def _run(tool, name, arguments):
    return await tool.execute(tool_name=name, arguments=arguments, user_id="member-1", agent_tool_permissions=[name])


READ_DAY = {"start_time": "2026-10-22T00:00:00", "end_time": "2026-10-22T23:59:59"}


@pytest.mark.asyncio
async def test_calendar_read_tells_the_model_which_calendar_each_event_is_on():
    tool = _tool(Account({HOME: [_event("Dinner", 10)], WORK: [_event("Dentist", 2)]}))

    read = await _run(tool, "calendar_read", READ_DAY)

    assert read.success, read.error
    assert [(e["title"], e["calendar"], e["calendar_name"]) for e in read.data["events"]] == [
        ("Dentist", "c2", "Work"),
        ("Dinner", "c1", "Home"),
    ]
    assert all("read_only" not in e for e in read.data["events"])


@pytest.mark.asyncio
async def test_calendar_read_marks_an_event_the_member_cannot_change():
    sources = FakeSources([CalendarSource(key="c1", url=HOLIDAYS.url, name="Holidays", writable=False, picked=True)])
    tool = _tool(Account({HOLIDAYS: [_event("Bank holiday", 0)]}), sources)

    read = await _run(tool, "calendar_read", READ_DAY)

    assert read.data["events"][0]["read_only"] is True


@pytest.mark.asyncio
async def test_calendar_read_names_a_calendar_it_could_not_read():
    account = Account({HOME: [_event("Dinner", 10)], WORK: [_event("Dentist", 2)]})
    account.down = {WORK.url}
    tool = _tool(account)

    read = await _run(tool, "calendar_read", READ_DAY)

    assert read.success
    assert read.data["unreadable"] == ["Work"]


@pytest.mark.asyncio
async def test_calendar_write_adds_to_the_calendar_named_and_says_which():
    account = Account({HOME: [], WORK: []})
    tool = _tool(account)

    created = await _run(tool, "calendar_write", {
        "action": "create", "title": "Dinner", "calendar": "c2",
        "start_time": "2026-10-24T19:00:00", "end_time": "2026-10-24T21:00:00",
    })

    assert created.success, created.error
    assert account.wrote_to == [WORK.url]
    assert created.data["calendar"] == "c2"


@pytest.mark.asyncio
async def test_calendar_write_without_a_calendar_when_there_are_two_fails_telling_the_model_to_ask():
    account = Account({HOME: [], WORK: []})
    tool = _tool(account)

    created = await _run(tool, "calendar_write", {
        "action": "create", "title": "Dinner", "start_time": "2026-10-24T19:00:00", "end_time": "2026-10-24T21:00:00",
    })

    assert not created.success
    assert "ask the member" in created.error.lower()


@pytest.mark.asyncio
async def test_calendar_write_changes_and_removes_on_the_calendar_named():
    account = Account({HOME: [], WORK: []})
    tool = _tool(account)

    await _run(tool, "calendar_write", {"action": "update", "event_id": "dinner-uid", "title": "x", "calendar": "c2"})
    await _run(tool, "calendar_write", {"action": "delete", "event_id": "dinner-uid", "calendar": "c1"})

    assert account.wrote_to == [WORK.url, HOME.url]


def test_calendar_write_offers_a_calendar_argument_and_no_longer_speaks_of_one_primary_calendar():
    write = next(t for t in ListAvailableToolsUseCase().execute() if t.name == "calendar_write")

    assert "calendar" in write.parameters_schema["properties"]
    assert "primary" not in write.description.lower()


@pytest.mark.asyncio
async def test_calendar_read_lists_every_calendar_it_read_even_one_with_nothing_that_day():
    sources = FakeSources([
        CalendarSource(key="c1", url=HOME.url, name="Home", writable=True, picked=True),
        CalendarSource(key="c2", url=WORK.url, name="Work", writable=True, picked=False),
        CalendarSource(key="c3", url=HOLIDAYS.url, name="Holidays", writable=False, picked=True),
    ])
    tool = _tool(Account({HOME: [], WORK: [_event("Dentist", 2)], HOLIDAYS: []}), sources)

    read = await _run(tool, "calendar_read", READ_DAY)

    assert read.data["calendars"] == [
        {"calendar": "c1", "name": "Home"},
        {"calendar": "c3", "name": "Holidays", "read_only": True},
    ]
