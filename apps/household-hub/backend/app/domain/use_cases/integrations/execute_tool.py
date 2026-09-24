from datetime import datetime, timezone
from typing import Any, Dict, List, Optional
from app.domain.entities.tool_definition import ToolExecutionResult
from app.domain.exceptions import (
    ToolNotFoundException,
    ToolPermissionDeniedException,
    SecretModeLockException,
    DomainException,
    PageReadException,
    ToolFailureReason,
)
from app.domain.repositories.calendar_credential_repository import ICalendarCredentialRepository
from app.domain.repositories.calendar_connector import ICalendarConnector
from app.domain.repositories.search_connector import ISearchConnector
from app.domain.repositories.document_repository import IDocumentRepository
from app.domain.repositories.document_reader import IDocumentReader
from app.domain.repositories.secret_cipher import ISecretCipher
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.repositories.page_reader import IPageReader

from app.domain.use_cases.integrations.get_calendar_events import GetCalendarEventsUseCase
from app.domain.use_cases.integrations.create_calendar_event import CreateCalendarEventUseCase
from app.domain.use_cases.integrations.update_calendar_event import UpdateCalendarEventUseCase
from app.domain.use_cases.integrations.delete_calendar_event import DeleteCalendarEventUseCase
from app.domain.use_cases.integrations.execute_search import ExecuteSearchUseCase
from app.domain.use_cases.integrations.manage_documents import SaveDocumentUseCase
from app.domain.use_cases.integrations.turn_sources import TurnSources


# Reading pages and looking up what was read are part of searching: an agent that may search may do both.
RESEARCH_TOOLS = ["read_page", "lookup_sources"]

# What a search result shows in the prompt. With a turn index the rest is in the index, so the
# receipt only has to say what each result is; without one the snippet is all the model gets.
RECEIPT_SNIPPET_CHARACTERS = 120
INLINE_SNIPPET_CHARACTERS = 300
DEFAULT_SEARCH_RESULTS = 5
DEFAULT_LOOKUP_PASSAGES = 5
READ_PAGE_PASSAGES = 3


def effective_tool_permissions(permissions: List[str]) -> List[str]:
    """The tools an agent may use: what it was given, plus reading and looking up when it may search."""
    granted = list(permissions)
    if "searxng_search" in granted:
        granted += [tool for tool in RESEARCH_TOOLS if tool not in granted]
    return granted


class ExecuteToolUseCase:
    def __init__(
        self,
        calendar_repo: ICalendarCredentialRepository,
        calendar_connector: ICalendarConnector,
        search_connector: ISearchConnector,
        document_repo: IDocumentRepository,
        document_reader: IDocumentReader,
        cipher: ISecretCipher,
        uow: IUnitOfWork,
        allow_calendar_delete: bool = True,
        page_reader: Optional[IPageReader] = None,
    ):
        self.calendar_repo = calendar_repo
        self.calendar_connector = calendar_connector
        self.search_connector = search_connector
        self.document_repo = document_repo
        self.document_reader = document_reader
        self.cipher = cipher
        self.uow = uow
        self.allow_calendar_delete = allow_calendar_delete
        self.page_reader = page_reader

        # Initialize sub-use-cases
        self.get_calendar_events_uc = GetCalendarEventsUseCase(calendar_repo, calendar_connector, cipher)
        self.create_calendar_event_uc = CreateCalendarEventUseCase(calendar_repo, calendar_connector, cipher)
        self.update_calendar_event_uc = UpdateCalendarEventUseCase(calendar_repo, calendar_connector, cipher)
        self.delete_calendar_event_uc = DeleteCalendarEventUseCase(
            calendar_repo, calendar_connector, cipher, allow_agent_delete=allow_calendar_delete
        )
        self.search_uc = ExecuteSearchUseCase(search_connector)
        self.save_doc_uc = SaveDocumentUseCase(document_repo, uow)

    async def execute(
        self,
        tool_name: str,
        arguments: Dict[str, Any],
        user_id: str,
        agent_tool_permissions: List[str],
        is_secret_mode: bool = False,
        role: str = "assistant",
        sources: Optional[TurnSources] = None,
    ) -> ToolExecutionResult:
        # 1. Verify agent tool permission
        if tool_name not in effective_tool_permissions(agent_tool_permissions):
            raise ToolPermissionDeniedException(
                f"Agent does not have permission to execute tool '{tool_name}'."
            )

        # 2. Enforce Secret Mode lock on external write operations
        if is_secret_mode and tool_name in {"calendar_write", "document_writer"}:
            return ToolExecutionResult(
                tool_name=tool_name,
                success=False,
                error="External write tools are disabled in Secret Mode to ensure zero-leak confidentiality.",
            )

        # 3. Dispatch to target tool implementation with soft degradation
        try:
            if tool_name == "searxng_search":
                query = arguments.get("query", "")
                fresh = bool(arguments.get("fresh", False))
                limit = int(arguments.get("limit", DEFAULT_SEARCH_RESULTS))
                res = await self.search_uc.execute(query=query, role=role, fresh=fresh, limit=limit)
                if sources is None:
                    results = [
                        {"title": r.title, "url": r.url, "snippet": r.snippet[:INLINE_SNIPPET_CHARACTERS]}
                        for r in res.results
                    ]
                else:
                    results = [
                        {"id": p.id, "title": p.title, "url": p.url, "snippet": p.text[:RECEIPT_SNIPPET_CHARACTERS]}
                        for p in await sources.add_search_results(res.results)
                    ]
                return ToolExecutionResult(
                    tool_name=tool_name,
                    success=True,
                    data={"query": res.query, "results": results},
                )

            elif tool_name in RESEARCH_TOOLS and sources is None:
                return ToolExecutionResult(
                    tool_name=tool_name,
                    success=False,
                    error=f"'{tool_name}' only works during a chat turn, on what that turn has searched and read.",
                )

            elif tool_name == "read_page":
                source = str(arguments.get("source", "")).strip()
                url = sources.url_for(source)
                if url is None:
                    return ToolExecutionResult(
                        tool_name=tool_name,
                        success=False,
                        error=f"'{source}' is neither a URL nor a search result from this turn.",
                        reason=ToolFailureReason.NOT_FOUND,
                    )
                try:
                    page = await self.page_reader.read(url)
                except PageReadException as e:
                    # The id is resolved only here, so the failed result says which page it was.
                    return ToolExecutionResult(
                        tool_name=tool_name, success=False, data={"url": url}, error=e.message, reason=e.reason
                    )
                passages = await sources.add_page(page)
                data = {"page": sources.last_page_id, "title": page.title, "url": page.url, "passages": len(passages)}
                # Read with the question, the page's best passages come straight back: a model that
                # has to remember to look them up afterwards often doesn't, and then the page is wasted.
                question = str(arguments.get("question", "")).strip()
                if question:
                    data["relevant"] = [
                        {"id": p.id, "text": p.text}
                        for p in await sources.lookup(question, k=READ_PAGE_PASSAGES, within=sources.last_page_id)
                    ]
                return ToolExecutionResult(tool_name=tool_name, success=True, data=data)

            elif tool_name == "lookup_sources":
                question = str(arguments.get("question", ""))
                k = int(arguments.get("limit", DEFAULT_LOOKUP_PASSAGES))
                found = await sources.lookup(question, k=k)
                return ToolExecutionResult(
                    tool_name=tool_name,
                    success=True,
                    data={
                        "passages": [{"id": p.id, "title": p.title, "url": p.url, "text": p.text} for p in found]
                    },
                )

            elif tool_name == "calendar_read":
                start_str = arguments.get("start_time")
                end_str = arguments.get("end_time")
                if not start_str or not end_str:
                    return ToolExecutionResult(
                        tool_name=tool_name,
                        success=False,
                        error="Both 'start_time' and 'end_time' are required ISO timestamps for calendar_read.",
                    )
                start_time = datetime.fromisoformat(start_str.replace("Z", "+00:00"))
                end_time = datetime.fromisoformat(end_str.replace("Z", "+00:00"))
                limit = int(arguments.get("limit", 50))

                events = await self.get_calendar_events_uc.execute(
                    user_id=user_id,
                    start_time=start_time,
                    end_time=end_time,
                    limit=limit,
                )
                return ToolExecutionResult(
                    tool_name=tool_name,
                    success=True,
                    data={
                        "events": [
                            {
                                "id": e.id,
                                "title": e.title,
                                "start_time": e.start_time.isoformat(),
                                "end_time": e.end_time.isoformat(),
                                "description": e.description,
                                "location": e.location,
                                "is_all_day": e.is_all_day,
                            }
                            for e in events
                        ]
                    },
                )

            elif tool_name == "calendar_write":
                action = arguments.get("action", "create")
                if action == "create":
                    title = arguments.get("title", "Untitled Event")
                    start_str = arguments.get("start_time")
                    end_str = arguments.get("end_time")
                    if not start_str or not end_str:
                        return ToolExecutionResult(
                            tool_name=tool_name,
                            success=False,
                            error="'start_time' and 'end_time' are required to create a calendar event.",
                        )
                    start_time = datetime.fromisoformat(start_str.replace("Z", "+00:00"))
                    end_time = datetime.fromisoformat(end_str.replace("Z", "+00:00"))
                    created = await self.create_calendar_event_uc.execute(
                        user_id=user_id,
                        title=title,
                        start_time=start_time,
                        end_time=end_time,
                        description=arguments.get("description", ""),
                        location=arguments.get("location", ""),
                        is_all_day=bool(arguments.get("is_all_day", False)),
                    )
                    return ToolExecutionResult(
                        tool_name=tool_name,
                        success=True,
                        data={
                            "action": "created",
                            "event_id": created.id,
                            "title": created.title,
                            "start_time": created.start_time.isoformat(),
                            "end_time": created.end_time.isoformat(),
                        },
                    )

                elif action == "update":
                    event_id = arguments.get("event_id")
                    if not event_id:
                        return ToolExecutionResult(
                            tool_name=tool_name,
                            success=False,
                            error="'event_id' is required to update a calendar event.",
                        )
                    start_time = None
                    if arguments.get("start_time"):
                        start_time = datetime.fromisoformat(arguments["start_time"].replace("Z", "+00:00"))
                    end_time = None
                    if arguments.get("end_time"):
                        end_time = datetime.fromisoformat(arguments["end_time"].replace("Z", "+00:00"))

                    updated = await self.update_calendar_event_uc.execute(
                        user_id=user_id,
                        event_id=event_id,
                        title=arguments.get("title"),
                        start_time=start_time,
                        end_time=end_time,
                        description=arguments.get("description"),
                        location=arguments.get("location"),
                        is_all_day=arguments.get("is_all_day"),
                    )
                    return ToolExecutionResult(
                        tool_name=tool_name,
                        success=True,
                        data={"action": "updated", "event_id": updated.id, "title": updated.title},
                    )

                elif action == "delete":
                    event_id = arguments.get("event_id")
                    if not event_id:
                        return ToolExecutionResult(
                            tool_name=tool_name,
                            success=False,
                            error="'event_id' is required to delete a calendar event.",
                        )
                    deleted = await self.delete_calendar_event_uc.execute(user_id=user_id, event_id=event_id)
                    return ToolExecutionResult(
                        tool_name=tool_name,
                        success=True,
                        data={"action": "deleted", "event_id": event_id, "deleted": deleted},
                    )
                else:
                    return ToolExecutionResult(
                        tool_name=tool_name,
                        success=False,
                        error=f"Unsupported calendar action '{action}'.",
                    )

            elif tool_name == "document_writer":
                title = arguments.get("title")
                content = arguments.get("content")
                if not title or content is None:
                    return ToolExecutionResult(
                        tool_name=tool_name,
                        success=False,
                        error="Both 'title' and 'content' are required for document_writer.",
                    )
                action = arguments.get("action", "create")
                saved_doc = await self.save_doc_uc.execute(
                    user_id=user_id,
                    title=title,
                    content=content,
                    action=action,
                )
                return ToolExecutionResult(
                    tool_name=tool_name,
                    success=True,
                    data={
                        "document_id": saved_doc.id,
                        "title": saved_doc.title,
                        "action": action,
                        "version": saved_doc.version,
                    },
                )

            elif tool_name == "pdf_reader":
                return ToolExecutionResult(
                    tool_name=tool_name,
                    success=False,
                    error="Use the dedicated POST /api/v1/integrations/documents/pdf endpoint to upload and parse PDFs.",
                )

            else:
                raise ToolNotFoundException(f"Unknown tool '{tool_name}'.")

        except DomainException as e:
            return ToolExecutionResult(
                tool_name=tool_name,
                success=False,
                error=e.message,
                reason=getattr(e, "reason", None),
            )
        except Exception as e:
            return ToolExecutionResult(
                tool_name=tool_name,
                success=False,
                error=str(e),
            )
