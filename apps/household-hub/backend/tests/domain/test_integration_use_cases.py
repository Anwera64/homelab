from datetime import datetime, timezone
from typing import Any, Dict, List, Optional
import pytest

from app.domain.entities.integration_credential import CalendarCredential
from app.domain.entities.calendar_event import CalendarEvent
from app.domain.entities.search_result import SearchResult, SearchResultItem
from app.domain.entities.document import DocumentMetadata, DocumentSection, ParsedDocument, StoredDocument
from app.domain.entities.tool_definition import ToolDefinition, ToolExecutionResult
from app.domain.exceptions import (
    CalendarAuthException,
    CalendarIntegrationException,
    CalendarUnreachableException,
    DocumentParsingException,
    DocumentNotFoundException,
    InvalidOperationException,
    ToolNotFoundException,
    ToolPermissionDeniedException,
    SecretModeLockException,
)

# Use Cases to test
from app.domain.use_cases.integrations.configure_calendar import ConfigureCalendarUseCase
from app.domain.use_cases.integrations.get_user_calendar import GetUserCalendarUseCase
from app.domain.use_cases.integrations.delete_calendar import DeleteCalendarUseCase
from app.domain.use_cases.integrations.get_calendar_events import GetCalendarEventsUseCase
from app.domain.use_cases.integrations.create_calendar_event import CreateCalendarEventUseCase
from app.domain.use_cases.integrations.update_calendar_event import UpdateCalendarEventUseCase
from app.domain.use_cases.integrations.delete_calendar_event import DeleteCalendarEventUseCase
from app.domain.use_cases.integrations.execute_search import ExecuteSearchUseCase
from app.domain.use_cases.integrations.parse_pdf_document import ParsePdfDocumentUseCase
from app.domain.use_cases.integrations.manage_documents import (
    SaveDocumentUseCase,
    GetDocumentUseCase,
    ListDocumentsUseCase,
    DeleteDocumentUseCase,
)
from app.domain.use_cases.integrations.list_available_tools import ListAvailableToolsUseCase
from app.domain.use_cases.integrations.execute_tool import ExecuteToolUseCase
from app.domain.use_cases.integrations.calendar_secret_resolver import CalendarSecretResolver


# Mock Implementations for Protocols
class MockUnitOfWork:
    def __init__(self):
        self.committed = False

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        pass

    async def commit(self):
        self.committed = True

    async def rollback(self):
        pass


class MockSecretCipher:
    def encrypt(self, plaintext: str) -> str:
        return f"ENC:{plaintext}"

    def decrypt(self, ciphertext: str) -> str:
        if ciphertext.startswith("ENC:"):
            return ciphertext[4:]
        return ciphertext


class MockCalendarCredentialRepository:
    def __init__(self):
        self.credentials: Dict[str, CalendarCredential] = {}

    async def get_by_user_id(self, user_id: str) -> Optional[CalendarCredential]:
        return self.credentials.get(user_id)

    async def save(self, credential: CalendarCredential) -> CalendarCredential:
        self.credentials[credential.user_id] = credential
        return credential

    async def delete_by_user_id(self, user_id: str) -> bool:
        if user_id in self.credentials:
            del self.credentials[user_id]
            return True
        return False


class MockCalendarConnector:
    def __init__(self, should_auth_fail: bool = False, connection_error: Optional[Exception] = None):
        self.should_auth_fail = should_auth_fail
        self.connection_error = connection_error
        self.events: List[CalendarEvent] = []
        # What the last update or delete said about a repeating event's dates.
        self.series_calls: List[dict] = []

    async def test_connection(self, credential: CalendarCredential, secret: str) -> bool:
        if self.connection_error is not None:
            raise self.connection_error
        return not self.should_auth_fail

    async def fetch_events(
        self,
        credential: CalendarCredential,
        secret: str,
        start_time: datetime,
        end_time: datetime,
        limit: int = 50,
        timeout: float = 10.0,
    ) -> List[CalendarEvent]:
        return self.events

    async def create_event(
        self,
        credential: CalendarCredential,
        secret: str,
        title: str,
        start_time: datetime,
        end_time: datetime,
        description: str = "",
        location: str = "",
        is_all_day: bool = False,
        timeout: float = 10.0,
        repeat=None,
    ) -> CalendarEvent:
        event = CalendarEvent(
            id=f"evt-{len(self.events) + 1}",
            title=title,
            start_time=start_time,
            end_time=end_time,
            description=description,
            location=location,
            is_all_day=is_all_day,
            calendar_name=credential.calendar_name,
            repeat=repeat,
        )
        self.events.append(event)
        return event

    async def update_event(
        self,
        credential: CalendarCredential,
        secret: str,
        event_id: str,
        title: Optional[str] = None,
        start_time: Optional[datetime] = None,
        end_time: Optional[datetime] = None,
        description: Optional[str] = None,
        location: Optional[str] = None,
        is_all_day: Optional[bool] = None,
        timeout: float = 10.0,
        repeat=None,
        occurrence_start: Optional[datetime] = None,
        scope: Optional[str] = None,
    ) -> CalendarEvent:
        self.series_calls.append({"occurrence_start": occurrence_start, "scope": scope, "repeat": repeat})
        for evt in self.events:
            if evt.id == event_id:
                if title:
                    evt.title = title
                if start_time:
                    evt.start_time = start_time
                if end_time:
                    evt.end_time = end_time
                if description is not None:
                    evt.description = description
                if location is not None:
                    evt.location = location
                if is_all_day is not None:
                    evt.is_all_day = is_all_day
                return evt
        raise CalendarIntegrationException(f"Event {event_id} not found")

    async def delete_event(
        self,
        credential: CalendarCredential,
        secret: str,
        event_id: str,
        timeout: float = 10.0,
        occurrence_start: Optional[datetime] = None,
        scope: Optional[str] = None,
    ) -> bool:
        self.series_calls.append({"occurrence_start": occurrence_start, "scope": scope})
        for i, evt in enumerate(self.events):
            if evt.id == event_id:
                del self.events[i]
                return True
        return False


class MockSearchConnector:
    def __init__(self):
        self.last_query: Optional[str] = None
        self.last_category: Optional[str] = None
        self.last_engines: Optional[List[str]] = None
        self.last_fresh: bool = False

    async def search(
        self,
        query: str,
        category: str = "general",
        engines: Optional[List[str]] = None,
        fresh: bool = False,
        limit: int = 10,
        timeout: float = 8.0,
    ) -> SearchResult:
        self.last_query = query
        self.last_category = category
        self.last_engines = engines
        self.last_fresh = fresh
        return SearchResult(
            query=query,
            category=category,
            total_results=1,
            is_cached=not fresh,
            results=[
                SearchResultItem(
                    title=f"Result for {query}",
                    url="https://example.com",
                    snippet="Snippet text",
                    engine=engines[0] if engines else "ddg",
                )
            ],
        )

    async def ping(self, timeout: float = 3.0) -> bool:
        return True


class MockDocumentReader:
    async def parse_pdf(
        self,
        file_bytes: bytes,
        filename: str = "document.pdf",
        max_pages: int = 150,
        timeout: float = 30.0,
    ) -> ParsedDocument:
        if b"SCANNED_EMPTY" in file_bytes:
            from app.domain.exceptions import ScannedPdfException
            raise ScannedPdfException("Zero text characters found; image scan detected")
        return ParsedDocument(
            filename=filename,
            metadata=DocumentMetadata(
                title="Mock PDF", author="Tester", page_count=1, format="pdf", file_size_bytes=len(file_bytes)
            ),
            sections=[DocumentSection(heading="Intro", level=1, content="Hello PDF", page_number=1)],
            citations=["Ref 1"],
            plain_text="Hello PDF",
        )


class MockDocumentRepository:
    def __init__(self):
        self.documents: Dict[str, StoredDocument] = {}

    async def create(self, document: StoredDocument) -> StoredDocument:
        self.documents[document.id] = document
        return document

    async def get_by_id(self, document_id: str) -> Optional[StoredDocument]:
        return self.documents.get(document_id)

    async def get_by_user_and_title(self, user_id: str, title: str) -> Optional[StoredDocument]:
        for doc in self.documents.values():
            if doc.user_id == user_id and doc.title == title:
                return doc
        return None

    async def update(self, document: StoredDocument) -> StoredDocument:
        self.documents[document.id] = document
        return document

    async def list_by_user(self, user_id: str, space_id: Optional[str] = None) -> List[StoredDocument]:
        return [
            doc for doc in self.documents.values()
            if doc.user_id == user_id and (space_id is None or doc.space_id == space_id)
        ]

    async def delete(self, document_id: str) -> bool:
        if document_id in self.documents:
            del self.documents[document_id]
            return True
        return False


# Tests for Calendar Use Cases
@pytest.mark.asyncio
async def test_configure_calendar_success():
    repo = MockCalendarCredentialRepository()
    connector = MockCalendarConnector()
    cipher = MockSecretCipher()
    uow = MockUnitOfWork()

    use_case = ConfigureCalendarUseCase(repo, connector, cipher, uow)
    cred = await use_case.execute(
        user_id="u1",
        provider="apple_icloud",
        url="https://caldav.icloud.com",
        username="u1@icloud.com",
        password="secret-password",
        calendar_name="Personal",
    )

    assert cred.user_id == "u1"
    assert cred.encrypted_secret == "ENC:secret-password"
    assert cred.calendar_name == "Personal"
    assert uow.committed is True
    assert (await repo.get_by_user_id("u1")) is not None


@pytest.mark.asyncio
async def test_configure_calendar_auth_failure_raises():
    repo = MockCalendarCredentialRepository()
    connector = MockCalendarConnector(should_auth_fail=True)
    cipher = MockSecretCipher()
    uow = MockUnitOfWork()

    use_case = ConfigureCalendarUseCase(repo, connector, cipher, uow)
    with pytest.raises(CalendarAuthException):
        await use_case.execute(
            user_id="u1",
            provider="caldav",
            url="https://caldav.invalid",
            username="u1",
            password="bad",
        )
    assert (await repo.get_by_user_id("u1")) is None


class SpyMarkCalendarStepsFixed:
    """Records whose steps were marked fixed, and whether that was before the calendar was committed."""

    def __init__(self, uow: "MockUnitOfWork"):
        self.uow = uow
        self.calls: List[tuple] = []

    async def execute(self, user_id: str) -> None:
        self.calls.append((user_id, self.uow.committed))


@pytest.mark.asyncio
async def test_GIVEN_a_calendar_that_answers_WHEN_it_is_configured_THEN_the_members_failed_calendar_steps_are_marked_fixed_in_the_same_save():
    uow = MockUnitOfWork()
    mark_fixed = SpyMarkCalendarStepsFixed(uow)
    use_case = ConfigureCalendarUseCase(
        MockCalendarCredentialRepository(), MockCalendarConnector(), MockSecretCipher(), uow, mark_fixed=mark_fixed
    )

    await use_case.execute(
        user_id="u1", provider="apple_icloud", url="https://caldav.icloud.com", username="u1@icloud.com", password="pw"
    )

    assert mark_fixed.calls == [("u1", False)]
    assert uow.committed is True


@pytest.mark.asyncio
async def test_GIVEN_a_calendar_that_refuses_WHEN_it_is_configured_THEN_nothing_is_marked_fixed():
    uow = MockUnitOfWork()
    mark_fixed = SpyMarkCalendarStepsFixed(uow)
    use_case = ConfigureCalendarUseCase(
        MockCalendarCredentialRepository(),
        MockCalendarConnector(connection_error=CalendarAuthException("rejected")),
        MockSecretCipher(),
        uow,
        mark_fixed=mark_fixed,
    )

    with pytest.raises(CalendarAuthException):
        await use_case.execute(
            user_id="u1", provider="apple_icloud", url="https://caldav.icloud.com", username="u1@icloud.com", password="pw"
        )

    assert mark_fixed.calls == []


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "failure",
    [CalendarAuthException("rejected"), CalendarUnreachableException("unreachable")],
)
async def test_configure_calendar_keeps_the_connectors_reason_and_saves_nothing(failure):
    repo = MockCalendarCredentialRepository()
    connector = MockCalendarConnector(connection_error=failure)
    uow = MockUnitOfWork()

    use_case = ConfigureCalendarUseCase(repo, connector, MockSecretCipher(), uow)
    with pytest.raises(type(failure)):
        await use_case.execute(
            user_id="u1",
            provider="apple_icloud",
            url="https://caldav.icloud.com",
            username="emma@icloud.com",
            password="abcd-efgh-ijkl-mnop",
        )
    assert (await repo.get_by_user_id("u1")) is None
    assert uow.committed is False


@pytest.mark.asyncio
async def test_calendar_event_lifecycle():
    repo = MockCalendarCredentialRepository()
    connector = MockCalendarConnector()
    cipher = MockSecretCipher()
    uow = MockUnitOfWork()
    secrets = CalendarSecretResolver(repo, cipher, uow)

    # Pre-configure calendar
    cred = CalendarCredential(
        user_id="u1",
        provider="google_caldav",
        url="https://caldav.google.com",
        username="u1@gmail.com",
        encrypted_secret="ENC:pass123",
        calendar_name="Primary",
    )
    await repo.save(cred)

    # Create event
    now = datetime.now(timezone.utc)
    create_uc = CreateCalendarEventUseCase(repo, connector, secrets)
    event = await create_uc.execute(
        user_id="u1",
        title="Sync Meeting",
        start_time=now,
        end_time=now,
        description="Discuss roadmap",
        location="Office",
    )
    assert event.id == "evt-1"
    assert event.title == "Sync Meeting"

    # Fetch events
    get_events_uc = GetCalendarEventsUseCase(repo, connector, secrets)
    events = await get_events_uc.execute(user_id="u1", start_time=now, end_time=now)
    assert len(events) == 1

    # Update event
    update_uc = UpdateCalendarEventUseCase(repo, connector, secrets)
    updated = await update_uc.execute(user_id="u1", event_id=event.id, title="Updated Sync Meeting")
    assert updated.title == "Updated Sync Meeting"

    # Delete event with safety switch allowed
    delete_uc = DeleteCalendarEventUseCase(repo, connector, secrets, allow_agent_delete=True)
    deleted = await delete_uc.execute(user_id="u1", event_id=event.id)
    assert deleted is True

    # Delete event with safety switch blocked
    delete_blocked_uc = DeleteCalendarEventUseCase(repo, connector, secrets, allow_agent_delete=False)
    with pytest.raises(ToolPermissionDeniedException):
        await delete_blocked_uc.execute(user_id="u1", event_id="evt-2")


# Tests for SearXNG Use Case
@pytest.mark.asyncio
async def test_GIVEN_no_category_WHEN_searching_THEN_it_is_a_general_search_on_the_default_engines():
    connector = MockSearchConnector()
    use_case = ExecuteSearchUseCase(connector)

    res = await use_case.execute(query="best dinner recipes", fresh=False)

    assert connector.last_category == "general"
    assert connector.last_engines is None
    assert connector.last_fresh is False
    assert len(res.results) == 1


@pytest.mark.asyncio
async def test_GIVEN_the_science_category_WHEN_searching_THEN_only_the_academic_engines_are_asked():
    connector = MockSearchConnector()
    use_case = ExecuteSearchUseCase(connector)

    await use_case.execute(query="parametric roofs", category="science", fresh=True)

    assert connector.last_category == "science"
    assert connector.last_engines == ["arxiv", "wikipedia", "wikidata", "wolframalpha"]
    assert connector.last_fresh is True


@pytest.mark.asyncio
async def test_GIVEN_an_unknown_category_WHEN_searching_THEN_it_falls_back_to_a_general_search():
    connector = MockSearchConnector()
    use_case = ExecuteSearchUseCase(connector)

    await use_case.execute(query="parametric roofs", category="scholarly")

    assert connector.last_category == "general"
    assert connector.last_engines is None


# Tests for PDF Use Case
@pytest.mark.asyncio
async def test_parse_pdf_document_bounds_and_scanned_check():
    reader = MockDocumentReader()
    use_case = ParsePdfDocumentUseCase(reader, max_size_bytes=1000)

    # Within size limit
    parsed = await use_case.execute(file_bytes=b"PDF content", filename="paper.pdf")
    assert parsed.filename == "paper.pdf"
    assert parsed.plain_text == "Hello PDF"

    # Exceeding size limit
    with pytest.raises(DocumentParsingException):
        await use_case.execute(file_bytes=b"x" * 2000, filename="big.pdf")

    # Scanned PDF without text layer
    with pytest.raises(DocumentParsingException):
        await use_case.execute(file_bytes=b"SCANNED_EMPTY_PDF", filename="scanned.pdf")


# Tests for Document Management
@pytest.mark.asyncio
async def test_document_storage_create_append_replace():
    repo = MockDocumentRepository()
    uow = MockUnitOfWork()
    save_uc = SaveDocumentUseCase(repo, uow)
    get_uc = GetDocumentUseCase(repo)

    # 1. Create action
    doc1 = await save_uc.execute(
        user_id="u1",
        title="Literature Review",
        content="# Intro\nStarting notes.",
        action="create",
    )
    assert doc1.version == 1
    assert doc1.content == "# Intro\nStarting notes."

    # 2. Append action
    doc2 = await save_uc.execute(
        user_id="u1",
        title="Literature Review",
        content="\n\n## Section 2\nAppended notes.",
        action="append",
    )
    assert doc2.version == 2
    assert "Section 2" in doc2.content
    assert "# Intro" in doc2.content

    # 3. Replace action
    doc3 = await save_uc.execute(
        user_id="u1",
        title="Literature Review",
        content="# Full Rewrite",
        action="replace",
    )
    assert doc3.version == 3
    assert doc3.content == "# Full Rewrite"


# Tests for Tool Dispatcher & OpenAI Schemas
@pytest.mark.asyncio
async def test_list_available_tools_openai_schemas():
    use_case = ListAvailableToolsUseCase()
    tools = use_case.execute()
    assert len(tools) == 7
    tool_names = {t.name for t in tools}
    assert tool_names == {
        "calendar_read",
        "calendar_write",
        "searxng_search",
        "read_page",
        "lookup_sources",
        "pdf_reader",
        "document_writer",
    }
    for t in tools:
        assert "type" in t.parameters_schema
        assert "properties" in t.parameters_schema


def test_GIVEN_the_tool_catalog_WHEN_searxng_search_is_offered_THEN_the_model_can_choose_general_or_science():
    search = next(t for t in ListAvailableToolsUseCase().execute() if t.name == "searxng_search")

    category = search.parameters_schema["properties"]["category"]

    assert category["enum"] == ["general", "science"]
    assert category["default"] == "general"
    assert "category" not in search.parameters_schema["required"]


def test_GIVEN_the_science_category_WHEN_described_to_the_model_THEN_it_names_no_engines_it_cannot_promise():
    """SearXNG answers a science search from its whole science category (PubMed, Semantic Scholar...),
    not only the engines the hub asks for, so the description must not promise a fixed list."""
    search = next(t for t in ListAvailableToolsUseCase().execute() if t.name == "searxng_search")

    description = search.parameters_schema["properties"]["category"]["description"]

    for engine in ("arXiv", "Wikipedia", "Wikidata", "Wolfram Alpha"):
        assert engine not in description


def test_the_model_is_not_offered_a_way_around_the_search_cache():
    """A model retrying with `fresh` hit throttled engines again and again (#42)."""
    search = next(t for t in ListAvailableToolsUseCase().execute() if t.name == "searxng_search")

    assert "fresh" not in search.parameters_schema["properties"]


def _search_tool(connector: MockSearchConnector) -> ExecuteToolUseCase:
    return ExecuteToolUseCase(
        calendar_repo=MockCalendarCredentialRepository(),
        calendar_connector=MockCalendarConnector(),
        search_connector=connector,
        document_repo=MockDocumentRepository(),
        document_reader=MockDocumentReader(),
        cipher=MockSecretCipher(),
        uow=MockUnitOfWork(),
    )


@pytest.mark.asyncio
async def test_GIVEN_the_model_asks_for_science_WHEN_searxng_search_runs_THEN_the_academic_engines_are_searched():
    connector = MockSearchConnector()

    result = await _search_tool(connector).execute(
        tool_name="searxng_search",
        arguments={"query": "parametric roofs", "category": "science"},
        user_id="u1",
        agent_tool_permissions=["searxng_search"],
    )

    assert result.success is True
    assert connector.last_category == "science"
    assert connector.last_engines == ["arxiv", "wikipedia", "wikidata", "wolframalpha"]


@pytest.mark.asyncio
async def test_GIVEN_no_category_WHEN_searxng_search_runs_THEN_it_is_a_general_search():
    connector = MockSearchConnector()

    await _search_tool(connector).execute(
        tool_name="searxng_search",
        arguments={"query": "human rights report 2025"},
        user_id="u1",
        agent_tool_permissions=["searxng_search"],
    )

    assert connector.last_category == "general"
    assert connector.last_engines is None


@pytest.mark.asyncio
async def test_a_model_asking_for_a_fresh_search_still_gets_the_cache():
    connector = MockSearchConnector()

    await _search_tool(connector).execute(
        tool_name="searxng_search",
        arguments={"query": "peru", "fresh": True},
        user_id="u1",
        agent_tool_permissions=["searxng_search"],
    )

    assert connector.last_fresh is False


@pytest.mark.asyncio
async def test_execute_tool_permissions_and_secret_mode():
    cal_repo = MockCalendarCredentialRepository()
    cal_connector = MockCalendarConnector()
    search_connector = MockSearchConnector()
    doc_repo = MockDocumentRepository()
    doc_reader = MockDocumentReader()
    cipher = MockSecretCipher()
    uow = MockUnitOfWork()

    execute_uc = ExecuteToolUseCase(
        calendar_repo=cal_repo,
        calendar_connector=cal_connector,
        search_connector=search_connector,
        document_repo=doc_repo,
        document_reader=doc_reader,
        cipher=cipher,
        uow=uow,
        allow_calendar_delete=True,
    )

    # 1. Permission check failure
    with pytest.raises(ToolPermissionDeniedException):
        await execute_uc.execute(
            tool_name="calendar_write",
            arguments={"title": "Test"},
            user_id="u1",
            agent_tool_permissions=["searxng_search"],  # Missing calendar_write
            is_secret_mode=False,
        )

    # 2. Secret Mode Lock check (Soft degradation)
    secret_res = await execute_uc.execute(
        tool_name="calendar_write",
        arguments={"title": "Surprise Dinner"},
        user_id="u1",
        agent_tool_permissions=["calendar_write"],
        is_secret_mode=True,  # Secret Mode!
    )
    assert secret_res.success is False
    assert "Secret Mode" in secret_res.error

    # 3. Successful SearXNG execution via dispatcher
    search_res = await execute_uc.execute(
        tool_name="searxng_search",
        arguments={"query": "Barcelona weather", "fresh": True},
        user_id="u1",
        agent_tool_permissions=["searxng_search"],
        is_secret_mode=False,
    )
    assert search_res.success is True
    assert search_res.data["query"] == "Barcelona weather"

    # 4. Soft Degradation on unconfigured calendar
    cal_res = await execute_uc.execute(
        tool_name="calendar_read",
        arguments={"start_time": "2026-09-08T00:00:00Z", "end_time": "2026-09-09T00:00:00Z"},
        user_id="u1",
        agent_tool_permissions=["calendar_read"],
        is_secret_mode=False,
    )
    assert cal_res.success is False
    assert "No calendar configured" in cal_res.error


# A calendar tool that fails says why, so the phone can offer the fix (slice 4, PR 7).
class RefusingCalendarConnector(MockCalendarConnector):
    """A calendar whose server now refuses the stored password or token."""

    async def fetch_events(self, *args, **kwargs):
        raise CalendarAuthException("CalDAV server refused the credentials")

    async def create_event(self, *args, **kwargs):
        raise CalendarAuthException("CalDAV server refused the credentials")


def _execute_tool(cal_repo, cal_connector, **options) -> ExecuteToolUseCase:
    return ExecuteToolUseCase(
        calendar_repo=cal_repo,
        calendar_connector=cal_connector,
        search_connector=MockSearchConnector(),
        document_repo=MockDocumentRepository(),
        document_reader=MockDocumentReader(),
        cipher=MockSecretCipher(),
        uow=MockUnitOfWork(),
        **options,
    )


async def _connected_repo() -> MockCalendarCredentialRepository:
    repo = MockCalendarCredentialRepository()
    await repo.save(
        CalendarCredential(
            user_id="u1",
            provider="apple_icloud",
            url="https://caldav.icloud.com",
            username="emma@icloud.com",
            encrypted_secret="ENC:abcd-efgh-ijkl-mnop",
            calendar_name="Default",
        )
    )
    return repo


READ_ARGUMENTS = {"start_time": "2026-09-08T00:00:00Z", "end_time": "2026-09-09T00:00:00Z"}
ADD_ARGUMENTS = {
    "action": "create",
    "title": "Print shop cutoff",
    "start_time": "2026-09-08T16:00:00Z",
    "end_time": "2026-09-08T16:30:00Z",
}


@pytest.mark.asyncio
@pytest.mark.parametrize("tool, arguments", [("calendar_read", READ_ARGUMENTS), ("calendar_write", ADD_ARGUMENTS)])
async def test_GIVEN_the_calendar_refuses_the_sign_in_WHEN_a_calendar_tool_runs_THEN_it_fails_as_calendar_rejected(tool, arguments):
    execute_uc = _execute_tool(await _connected_repo(), RefusingCalendarConnector())

    result = await execute_uc.execute(tool_name=tool, arguments=arguments, user_id="u1", agent_tool_permissions=[tool])

    assert result.success is False
    assert result.reason == "calendar_rejected"


@pytest.mark.asyncio
@pytest.mark.parametrize("tool, arguments", [("calendar_read", READ_ARGUMENTS), ("calendar_write", ADD_ARGUMENTS)])
async def test_GIVEN_no_calendar_is_connected_WHEN_a_calendar_tool_runs_THEN_it_fails_as_calendar_not_connected(tool, arguments):
    execute_uc = _execute_tool(MockCalendarCredentialRepository(), MockCalendarConnector())

    result = await execute_uc.execute(tool_name=tool, arguments=arguments, user_id="u1", agent_tool_permissions=[tool])

    assert result.success is False
    assert result.reason == "calendar_not_connected"
    assert "No calendar configured" in result.error


@pytest.mark.asyncio
async def test_GIVEN_the_calendar_cannot_be_reached_WHEN_a_calendar_tool_runs_THEN_it_is_not_put_down_to_the_sign_in():
    class UnreachableCalendarConnector(MockCalendarConnector):
        async def fetch_events(self, *args, **kwargs):
            raise CalendarUnreachableException("CalDAV server did not answer")

    execute_uc = _execute_tool(await _connected_repo(), UnreachableCalendarConnector())

    result = await execute_uc.execute(
        tool_name="calendar_read", arguments=READ_ARGUMENTS, user_id="u1", agent_tool_permissions=["calendar_read"]
    )

    assert result.success is False
    assert result.reason not in ("calendar_rejected", "calendar_not_connected")


# Repeating events: calendar_write takes a repeat rule and says which dates a change is for.
@pytest.mark.asyncio
async def test_calendar_write_makes_a_repeating_event_and_read_says_how_it_repeats():
    from app.domain.entities.calendar_event import Repeat

    repo, connector = await _connected_repo(), MockCalendarConnector()
    tool = _execute_tool(repo, connector, allow_calendar_delete=True)

    created = await tool.execute(
        tool_name="calendar_write",
        arguments={
            "action": "create",
            "title": "Gym",
            "start_time": "2026-09-29T07:00:00Z",
            "end_time": "2026-09-29T08:00:00Z",
            "repeat": {"frequency": "weekly", "days": ["TU", "TH"], "until": "2026-12-24"},
        },
        user_id="u1",
        agent_tool_permissions=["calendar_write"],
    )
    assert created.success, created.error
    assert connector.events[0].repeat == Repeat(frequency="weekly", days=["TU", "TH"], until=datetime(2026, 12, 24).date())

    connector.events[0].occurrence_start = datetime(2026, 10, 1, 7, 0, tzinfo=timezone.utc)
    read = await tool.execute(
        tool_name="calendar_read",
        arguments={"start_time": "2026-09-28T00:00:00Z", "end_time": "2026-10-04T00:00:00Z"},
        user_id="u1",
        agent_tool_permissions=["calendar_read"],
    )
    (event,) = read.data["events"]
    assert event["occurrence_start"] == "2026-10-01T07:00:00+00:00"
    assert event["repeat"] == {"frequency": "weekly", "interval": 1, "days": ["TU", "TH"], "until": "2026-12-24"}


@pytest.mark.asyncio
async def test_calendar_write_passes_which_dates_of_a_repeating_event_it_means():
    repo, connector = await _connected_repo(), MockCalendarConnector()
    connector.events.append(
        CalendarEvent(id="gym-1", title="Gym", start_time=datetime(2026, 9, 29, 7, tzinfo=timezone.utc), end_time=datetime(2026, 9, 29, 8, tzinfo=timezone.utc))
    )
    tool = _execute_tool(repo, connector, allow_calendar_delete=True)

    await tool.execute(
        tool_name="calendar_write",
        arguments={"action": "update", "event_id": "gym-1", "title": "Swim", "occurrence_start": "2026-10-06T07:00:00Z", "scope": "following"},
        user_id="u1",
        agent_tool_permissions=["calendar_write"],
    )
    await tool.execute(
        tool_name="calendar_write",
        arguments={"action": "delete", "event_id": "gym-1", "occurrence_start": "2026-10-01T07:00:00Z"},
        user_id="u1",
        agent_tool_permissions=["calendar_write"],
    )

    moved, removed = connector.series_calls
    assert moved["scope"] == "following" and moved["occurrence_start"] == datetime(2026, 10, 6, 7, tzinfo=timezone.utc)
    assert removed["scope"] == "this" and removed["occurrence_start"] == datetime(2026, 10, 1, 7, tzinfo=timezone.utc)


@pytest.mark.asyncio
async def test_calendar_write_tells_the_model_what_is_wrong_with_a_repeat():
    repo, connector = await _connected_repo(), MockCalendarConnector()

    result = await _execute_tool(repo, connector, allow_calendar_delete=True).execute(
        tool_name="calendar_write",
        arguments={
            "action": "create",
            "title": "Gym",
            "start_time": "2026-09-29T07:00:00Z",
            "end_time": "2026-09-29T08:00:00Z",
            "repeat": {"frequency": "fortnightly"},
        },
        user_id="u1",
        agent_tool_permissions=["calendar_write"],
    )

    assert result.success is False and "repeat.frequency" in result.error
    assert connector.events == []


@pytest.mark.asyncio
async def test_GIVEN_the_calendar_did_not_keep_a_write_WHEN_calendar_write_runs_THEN_the_step_fails_and_says_what_the_calendar_has():
    from app.domain.exceptions import CalendarWriteNotConfirmedException

    class ForgetfulCalendarConnector(MockCalendarConnector):
        async def delete_event(self, *args, **kwargs):
            raise CalendarWriteNotConfirmedException("The calendar still has event 'gym-1' after removing it.")

    result = await _execute_tool(await _connected_repo(), ForgetfulCalendarConnector(), allow_calendar_delete=True).execute(
        tool_name="calendar_write",
        arguments={"action": "delete", "event_id": "gym-1"},
        user_id="u1",
        agent_tool_permissions=["calendar_write"],
    )

    assert result.success is False
    assert "still has event 'gym-1'" in result.error
