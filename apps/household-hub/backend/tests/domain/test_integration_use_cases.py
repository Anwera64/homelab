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
    def __init__(self, should_auth_fail: bool = False):
        self.should_auth_fail = should_auth_fail
        self.events: List[CalendarEvent] = []

    async def test_connection(self, credential: CalendarCredential, secret: str) -> bool:
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
    ) -> CalendarEvent:
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
    ) -> bool:
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


@pytest.mark.asyncio
async def test_calendar_event_lifecycle():
    repo = MockCalendarCredentialRepository()
    connector = MockCalendarConnector()
    cipher = MockSecretCipher()
    uow = MockUnitOfWork()

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
    create_uc = CreateCalendarEventUseCase(repo, connector, cipher)
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
    get_events_uc = GetCalendarEventsUseCase(repo, connector, cipher)
    events = await get_events_uc.execute(user_id="u1", start_time=now, end_time=now)
    assert len(events) == 1

    # Update event
    update_uc = UpdateCalendarEventUseCase(repo, connector, cipher)
    updated = await update_uc.execute(user_id="u1", event_id=event.id, title="Updated Sync Meeting")
    assert updated.title == "Updated Sync Meeting"

    # Delete event with safety switch allowed
    delete_uc = DeleteCalendarEventUseCase(repo, connector, cipher, allow_agent_delete=True)
    deleted = await delete_uc.execute(user_id="u1", event_id=event.id)
    assert deleted is True

    # Delete event with safety switch blocked
    delete_blocked_uc = DeleteCalendarEventUseCase(repo, connector, cipher, allow_agent_delete=False)
    with pytest.raises(ToolPermissionDeniedException):
        await delete_blocked_uc.execute(user_id="u1", event_id="evt-2")


# Tests for SearXNG Use Case
@pytest.mark.asyncio
async def test_execute_search_assistant_vs_researcher_profiles():
    connector = MockSearchConnector()
    use_case = ExecuteSearchUseCase(connector)

    # Assistant profile -> general search
    res_assistant = await use_case.execute(query="best dinner recipes", role="assistant", fresh=False)
    assert connector.last_category == "general"
    assert connector.last_fresh is False
    assert len(res_assistant.results) == 1

    # Researcher profile -> academic/science search
    res_researcher = await use_case.execute(query="parametric roofs", role="researcher", fresh=True)
    assert connector.last_category == "science"
    assert connector.last_engines == ["arxiv", "wikipedia", "wikidata", "wolframalpha"]
    assert connector.last_fresh is True


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
    assert len(tools) == 5
    tool_names = {t.name for t in tools}
    assert tool_names == {
        "calendar_read",
        "calendar_write",
        "searxng_search",
        "pdf_reader",
        "document_writer",
    }
    for t in tools:
        assert "type" in t.parameters_schema
        assert "properties" in t.parameters_schema


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
            role="assistant",
        )

    # 2. Secret Mode Lock check (Soft degradation)
    secret_res = await execute_uc.execute(
        tool_name="calendar_write",
        arguments={"title": "Surprise Dinner"},
        user_id="u1",
        agent_tool_permissions=["calendar_write"],
        is_secret_mode=True,  # Secret Mode!
        role="assistant",
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
        role="assistant",
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
        role="assistant",
    )
    assert cal_res.success is False
    assert "No calendar configured" in cal_res.error
