from typing import List
from app.domain.entities.integration_credential import CalendarCredential
from app.domain.entities.calendar_event import CalendarEvent
from app.domain.entities.search_result import SearchResult
from app.domain.entities.document import ParsedDocument, StoredDocument
from app.domain.entities.tool_definition import ToolDefinition, ToolExecutionResult

from app.presentation.schemas.integration_schemas import (
    CalendarCredentialRead,
    CalendarEventRead,
    SearchResultResponse,
    SearchResultItemResponse,
    DocumentParseResponse,
    DocumentSectionResponse,
    DocumentReadResponse,
    ToolDefinitionResponse,
    ToolExecutionResponse,
)


class IntegrationPresentationMapper:
    @staticmethod
    def to_calendar_credential_read(entity: CalendarCredential) -> CalendarCredentialRead:
        return CalendarCredentialRead(
            id=entity.id,
            user_id=entity.user_id,
            provider=entity.provider,
            url=entity.url,
            username=entity.username,
            calendar_name=entity.calendar_name,
            is_active=entity.is_active,
            created_at=entity.created_at,
            updated_at=entity.updated_at,
        )

    @staticmethod
    def to_calendar_event_read(entity: CalendarEvent) -> CalendarEventRead:
        return CalendarEventRead(
            id=entity.id,
            title=entity.title,
            start_time=entity.start_time,
            end_time=entity.end_time,
            description=entity.description,
            location=entity.location,
            is_all_day=entity.is_all_day,
            calendar_name=entity.calendar_name,
        )

    @staticmethod
    def to_search_result_response(entity: SearchResult) -> SearchResultResponse:
        return SearchResultResponse(
            query=entity.query,
            category=entity.category,
            total_results=entity.total_results,
            is_cached=entity.is_cached,
            results=[
                SearchResultItemResponse(
                    title=r.title,
                    url=r.url,
                    snippet=r.snippet,
                    engine=r.engine,
                    score=r.score,
                )
                for r in entity.results
            ],
        )

    @staticmethod
    def to_document_parse_response(entity: ParsedDocument) -> DocumentParseResponse:
        return DocumentParseResponse(
            filename=entity.filename,
            title=entity.metadata.title,
            author=entity.metadata.author,
            page_count=entity.metadata.page_count,
            sections=[
                DocumentSectionResponse(
                    heading=s.heading,
                    level=s.level,
                    content=s.content,
                    page_number=s.page_number,
                    is_citation=s.is_citation,
                )
                for s in entity.sections
            ],
            citations=entity.citations,
            plain_text=entity.plain_text,
        )

    @staticmethod
    def to_document_read_response(entity: StoredDocument) -> DocumentReadResponse:
        return DocumentReadResponse(
            id=entity.id,
            user_id=entity.user_id,
            space_id=entity.space_id,
            title=entity.title,
            content=entity.content,
            format=entity.format,
            version=entity.version,
            created_at=entity.created_at,
            updated_at=entity.updated_at,
        )

    @staticmethod
    def to_tool_definition_response(entity: ToolDefinition) -> ToolDefinitionResponse:
        return ToolDefinitionResponse(
            name=entity.name,
            description=entity.description,
            parameters_schema=entity.parameters_schema,
        )

    @staticmethod
    def to_tool_execution_response(entity: ToolExecutionResult) -> ToolExecutionResponse:
        return ToolExecutionResponse(
            tool_name=entity.tool_name,
            success=entity.success,
            data=entity.data,
            error=entity.error,
        )
