from typing import List
from app.domain.entities.tool_definition import ToolDefinition


class ListAvailableToolsUseCase:
    """Returns the catalog of homelab agent tools formatted with OpenAI-compatible JSON schemas."""

    def execute(self) -> List[ToolDefinition]:
        return [
            ToolDefinition(
                name="calendar_read",
                description="Fetch calendar events for the user within a specified time range.",
                parameters_schema={
                    "type": "object",
                    "properties": {
                        "start_time": {
                            "type": "string",
                            "description": "ISO 8601 start timestamp (e.g. 2026-09-08T00:00:00Z)",
                        },
                        "end_time": {
                            "type": "string",
                            "description": "ISO 8601 end timestamp (e.g. 2026-09-15T23:59:59Z)",
                        },
                        "limit": {
                            "type": "integer",
                            "description": "Maximum number of events to return (default: 50)",
                            "default": 50,
                        },
                    },
                    "required": ["start_time", "end_time"],
                },
            ),
            ToolDefinition(
                name="calendar_write",
                description="Schedule, update, or cancel a calendar event on the user's primary CalDAV calendar.",
                parameters_schema={
                    "type": "object",
                    "properties": {
                        "action": {
                            "type": "string",
                            "enum": ["create", "update", "delete"],
                            "description": "Action to perform on the calendar (default: create)",
                            "default": "create",
                        },
                        "title": {
                            "type": "string",
                            "description": "Title/summary of the calendar event",
                        },
                        "start_time": {
                            "type": "string",
                            "description": "ISO 8601 event start time",
                        },
                        "end_time": {
                            "type": "string",
                            "description": "ISO 8601 event end time",
                        },
                        "description": {
                            "type": "string",
                            "description": "Optional notes or details for the event",
                        },
                        "location": {
                            "type": "string",
                            "description": "Optional location for the event",
                        },
                        "is_all_day": {
                            "type": "boolean",
                            "description": "Whether the event is an all-day event",
                            "default": False,
                        },
                        "event_id": {
                            "type": "string",
                            "description": "Event ID required when action is 'update' or 'delete'",
                        },
                    },
                    "required": ["action"],
                },
            ),
            ToolDefinition(
                name="searxng_search",
                description=(
                    "Perform a private web or academic search via the homelab SearXNG aggregator. "
                    "Each result gets an id (s1, s2, ...): pass it to read_page to read the whole page."
                ),
                parameters_schema={
                    "type": "object",
                    "properties": {
                        "query": {
                            "type": "string",
                            "description": "The search keywords or query",
                        },
                        "limit": {
                            "type": "integer",
                            "description": "Number of search results to return (default: 5)",
                            "default": 5,
                        },
                    },
                    "required": ["query"],
                },
            ),
            ToolDefinition(
                name="read_page",
                description=(
                    "Read a whole web page or PDF from a search result or URL. Pass your question and "
                    "the page's most relevant passages come back; the rest is kept for lookup_sources."
                ),
                parameters_schema={
                    "type": "object",
                    "properties": {
                        "source": {
                            "type": "string",
                            "description": "A search result id such as 's3', or a full http(s) URL",
                        },
                        "question": {
                            "type": "string",
                            "description": "What you want to learn from this page",
                        },
                    },
                    "required": ["source"],
                },
            ),
            ToolDefinition(
                name="lookup_sources",
                description=(
                    "Search everything searched and read while answering, across all pages, for the "
                    "passages most relevant to a question. Cite passages by their ids."
                ),
                parameters_schema={
                    "type": "object",
                    "properties": {
                        "question": {
                            "type": "string",
                            "description": "What you need to know, in your own words",
                        },
                        "limit": {
                            "type": "integer",
                            "description": "Number of passages to return (default: 5)",
                            "default": 5,
                        },
                    },
                    "required": ["question"],
                },
            ),
            ToolDefinition(
                name="pdf_reader",
                description="Extract structured text, headings, page tags, and citations from an uploaded PDF document.",
                parameters_schema={
                    "type": "object",
                    "properties": {
                        "max_pages": {
                            "type": "integer",
                            "description": "Maximum number of pages to parse (default: 150)",
                            "default": 150,
                        },
                    },
                },
            ),
            ToolDefinition(
                name="document_writer",
                description="Create, append to, or replace a structured markdown note or research summary in SQLite.",
                parameters_schema={
                    "type": "object",
                    "properties": {
                        "title": {
                            "type": "string",
                            "description": "Title of the document/note",
                        },
                        "content": {
                            "type": "string",
                            "description": "Markdown content to write or append",
                        },
                        "action": {
                            "type": "string",
                            "enum": ["create", "append", "replace"],
                            "description": "Whether to create a new note, append to existing, or replace content",
                            "default": "create",
                        },
                    },
                    "required": ["title", "content"],
                },
            ),
        ]
