from datetime import datetime, timezone
import pytest

from app.domain.entities.integration_credential import CalendarCredential
from app.domain.entities.calendar_event import CalendarEvent
from app.domain.entities.search_result import SearchResultItem, SearchResult
from app.domain.entities.document import (
    DocumentMetadata,
    DocumentSection,
    ParsedDocument,
    StoredDocument,
)
from app.domain.entities.tool_definition import ToolDefinition, ToolExecutionResult
from app.domain.exceptions import (
    CalendarIntegrationException,
    CalendarAuthException,
    SearchServiceException,
    DocumentParsingException,
    ScannedPdfException,
    DocumentNotFoundException,
    ToolNotFoundException,
    ToolPermissionDeniedException,
    SecretModeLockException,
)


def test_calendar_credential_creation():
    cred = CalendarCredential(
        user_id="user-123",
        provider="apple_icloud",
        url="https://caldav.icloud.com",
        username="user@icloud.com",
        encrypted_secret="enc-secret-xyz",
        calendar_name="Home",
    )
    assert cred.id is not None
    assert cred.user_id == "user-123"
    assert cred.provider == "apple_icloud"
    assert cred.url == "https://caldav.icloud.com"
    assert cred.username == "user@icloud.com"
    assert cred.encrypted_secret == "enc-secret-xyz"
    assert cred.calendar_name == "Home"
    assert cred.is_active is True
    assert isinstance(cred.created_at, datetime)
    assert isinstance(cred.updated_at, datetime)


def test_calendar_event_creation():
    now = datetime.now(timezone.utc)
    event = CalendarEvent(
        id="evt-1",
        title="Family Dinner",
        start_time=now,
        end_time=now,
        description="Weekly dinner with family",
        location="Dining Room",
        is_all_day=False,
        calendar_name="Home",
    )
    assert event.id == "evt-1"
    assert event.title == "Family Dinner"
    assert event.start_time == now
    assert event.end_time == now
    assert event.location == "Dining Room"
    assert event.is_all_day is False


def test_search_result_and_items():
    item1 = SearchResultItem(
        title="Solar Power in Architecture",
        url="https://arxiv.org/abs/1234.5678",
        snippet="A study on integrated solar facades...",
        engine="arxiv",
        score=0.95,
    )
    result = SearchResult(
        query="solar facades architecture",
        category="science",
        total_results=1,
        is_cached=False,
        results=[item1],
    )
    assert result.query == "solar facades architecture"
    assert result.category == "science"
    assert result.total_results == 1
    assert result.is_cached is False
    assert len(result.results) == 1
    assert result.results[0].engine == "arxiv"


def test_document_entities_and_sections():
    meta = DocumentMetadata(
        title="Parametric Architectural Synthesis",
        author="UPC Lab",
        page_count=12,
        format="pdf",
        file_size_bytes=1048576,
    )
    section1 = DocumentSection(
        heading="Introduction",
        level=1,
        content="Parametric systems enable high adaptability...",
        page_number=1,
        is_citation=False,
    )
    section2 = DocumentSection(
        heading="References",
        level=1,
        content="[1] Smith et al. Architectural Computation, 2024.",
        page_number=12,
        is_citation=True,
    )
    parsed = ParsedDocument(
        filename="parametric_synthesis.pdf",
        metadata=meta,
        sections=[section1, section2],
        citations=["Smith et al. 2024"],
        plain_text="Full extracted text...",
    )
    assert parsed.filename == "parametric_synthesis.pdf"
    assert parsed.metadata.page_count == 12
    assert len(parsed.sections) == 2
    assert parsed.sections[0].heading == "Introduction"
    assert parsed.sections[1].is_citation is True
    assert len(parsed.citations) == 1

    stored = StoredDocument(
        id="doc-1",
        user_id="user-123",
        space_id="space-456",
        title="Research Notes",
        content="# Notes\nSummary of findings...",
        format="markdown",
        version=1,
    )
    assert stored.id == "doc-1"
    assert stored.version == 1
    assert stored.format == "markdown"


def test_tool_definition_and_execution_result():
    tool = ToolDefinition(
        name="searxng_search",
        description="Search the web or academic sources using SearXNG",
        parameters_schema={
            "type": "object",
            "properties": {
                "query": {"type": "string"},
                "fresh": {"type": "boolean", "default": False},
            },
            "required": ["query"],
        },
    )
    assert tool.name == "searxng_search"
    assert "query" in tool.parameters_schema["properties"]

    success_res = ToolExecutionResult(
        tool_name="searxng_search",
        success=True,
        data={"results": [{"title": "Test"}]},
        error=None,
    )
    assert success_res.success is True
    assert success_res.error is None

    fail_res = ToolExecutionResult(
        tool_name="calendar_write",
        success=False,
        data=None,
        error="CalDAV server connection timed out after 10s",
    )
    assert fail_res.success is False
    assert "timed out" in fail_res.error


def test_domain_exceptions_hierarchy():
    with pytest.raises(CalendarIntegrationException):
        raise CalendarAuthException("Invalid credentials")

    with pytest.raises(DocumentParsingException):
        raise ScannedPdfException("Zero text characters found; image scan detected")

    with pytest.raises(ToolPermissionDeniedException):
        raise SecretModeLockException("Writing is disabled in Secret Mode")
