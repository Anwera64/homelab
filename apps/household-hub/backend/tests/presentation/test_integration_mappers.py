from datetime import datetime, timezone
import pytest

from app.domain.entities.integration_credential import CalendarCredential
from app.domain.entities.calendar_event import CalendarEvent
from app.domain.entities.search_result import SearchResult, SearchResultItem
from app.domain.entities.document import DocumentMetadata, DocumentSection, ParsedDocument, StoredDocument
from app.domain.entities.tool_definition import ToolDefinition, ToolExecutionResult

from app.presentation.mappers.integration_presentation_mapper import IntegrationPresentationMapper


def test_integration_presentation_mapper_calendar():
    now = datetime.now(timezone.utc)
    cred = CalendarCredential(
        id="cred-1",
        user_id="u1",
        provider="apple_icloud",
        url="https://caldav.icloud.com",
        username="u1@icloud.com",
        encrypted_secret="enc-secret",
        calendar_name="Personal",
        is_active=True,
    )
    schema = IntegrationPresentationMapper.to_calendar_credential_read(cred)
    assert schema.id == "cred-1"
    assert schema.provider == "apple_icloud"
    assert schema.username == "u1@icloud.com"
    assert schema.calendar_name == "Personal"
    # Verify encrypted_secret is NOT exposed in the read schema!
    assert not hasattr(schema, "encrypted_secret")
    assert not hasattr(schema, "password")

    evt = CalendarEvent(
        id="evt-1",
        title="Dinner",
        start_time=now,
        end_time=now,
        description="Weekly dinner",
        location="Kitchen",
        is_all_day=False,
        calendar_name="Personal",
    )
    evt_schema = IntegrationPresentationMapper.to_calendar_event_read(evt)
    assert evt_schema.id == "evt-1"
    assert evt_schema.title == "Dinner"
    assert evt_schema.start_time == now


def test_integration_presentation_mapper_search_and_tools():
    result = SearchResult(
        query="parametric facade",
        category="science",
        total_results=1,
        is_cached=True,
        results=[
            SearchResultItem(
                title="Paper Title",
                url="https://arxiv.org/abs/1",
                snippet="Snippet...",
                engine="arxiv",
                score=0.9,
            )
        ],
    )
    schema = IntegrationPresentationMapper.to_search_result_response(result)
    assert schema.query == "parametric facade"
    assert schema.is_cached is True
    assert len(schema.results) == 1
    assert schema.results[0].engine == "arxiv"

    tool = ToolDefinition(
        name="searxng_search",
        description="Search description",
        parameters_schema={"type": "object", "properties": {"query": {"type": "string"}}},
    )
    tool_schema = IntegrationPresentationMapper.to_tool_definition_response(tool)
    assert tool_schema.name == "searxng_search"
    assert tool_schema.description == "Search description"
