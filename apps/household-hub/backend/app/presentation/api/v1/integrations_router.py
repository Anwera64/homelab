from datetime import datetime, timezone
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, status, UploadFile, File, Response, Query

from app.core.config import settings
from app.domain.entities.user import User
from app.presentation.api import deps as pres_deps
from app.presentation.mappers.integration_presentation_mapper import IntegrationPresentationMapper
from app.presentation.schemas.integration_schemas import (
    CalendarCredentialCreate,
    CalendarCredentialRead,
    CalendarEventCreate,
    CalendarEventUpdate,
    CalendarEventRead,
    SearchQueryRequest,
    SearchResultResponse,
    DocumentParseResponse,
    DocumentSaveRequest,
    DocumentReadResponse,
    ToolDefinitionResponse,
    ToolExecutionRequest,
    ToolExecutionResponse,
)

router = APIRouter(prefix="/integrations", tags=["integrations"])


# =========================================================================
# Tool Catalog & Execution Dispatcher
# =========================================================================
@router.get("/tools", response_model=List[ToolDefinitionResponse])
async def list_available_tools(
    current_user: User = Depends(pres_deps.get_current_user),
    tools_uc=Depends(pres_deps.get_list_available_tools_use_case),
):
    """Returns the catalog of available agent tools with OpenAI-compatible JSON parameter schemas."""
    tools = tools_uc.execute()
    return [IntegrationPresentationMapper.to_tool_definition_response(t) for t in tools]


@router.post("/tools/execute", response_model=ToolExecutionResponse)
async def execute_tool(
    payload: ToolExecutionRequest,
    current_user: User = Depends(pres_deps.get_current_user),
    agent_uc=Depends(pres_deps.get_agent_use_case),
    session_uc=Depends(pres_deps.get_session_use_case),
    execute_uc=Depends(pres_deps.get_execute_tool_use_case),
):
    """Executes a tool with strictly server-authoritative permissions check and Secret Mode enforcement."""
    session, _ = await session_uc.execute(session_id=payload.session_id, current_user=current_user)
    if session.is_archived:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Cannot execute tools in an archived conversation session.",
        )

    is_secret_mode = bool(session.is_secret)
    if not session.agent_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Session is not associated with an active agent personality.",
        )

    agent = await agent_uc.execute(session.agent_id)
    if not agent:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Assigned agent personality not found.",
        )

    result = await execute_uc.execute(
        tool_name=payload.tool_name,
        arguments=payload.arguments,
        user_id=current_user.id,
        agent_tool_permissions=agent.tool_permissions,
        is_secret_mode=is_secret_mode,
    )
    return IntegrationPresentationMapper.to_tool_execution_response(result)


# =========================================================================
# CalDAV Calendar Integration
# =========================================================================
@router.get("/calendars/me", response_model=CalendarCredentialRead)
async def get_my_calendar(
    current_user: User = Depends(pres_deps.get_current_user),
    get_uc=Depends(pres_deps.get_user_calendar_use_case),
):
    """Retrieves the authenticated user's active calendar configuration."""
    cred = await get_uc.execute(current_user.id)
    if not cred:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="No calendar configured.")
    return IntegrationPresentationMapper.to_calendar_credential_read(cred)


@router.post("/calendars", response_model=CalendarCredentialRead, status_code=status.HTTP_201_CREATED)
async def configure_calendar(
    payload: CalendarCredentialCreate,
    current_user: User = Depends(pres_deps.get_current_user),
    conf_uc=Depends(pres_deps.get_configure_calendar_use_case),
):
    """Configures or replaces the primary CalDAV calendar provider for the user."""
    cred = await conf_uc.execute(
        user_id=current_user.id,
        provider=payload.provider,
        url=payload.url,
        username=payload.username,
        password=payload.password,
        calendar_name=payload.calendar_name or "Default",
    )
    return IntegrationPresentationMapper.to_calendar_credential_read(cred)


@router.delete("/calendars", status_code=status.HTTP_204_NO_CONTENT)
async def delete_calendar(
    current_user: User = Depends(pres_deps.get_current_user),
    del_uc=Depends(pres_deps.get_delete_calendar_use_case),
):
    """Removes the authenticated user's calendar configuration."""
    await del_uc.execute(current_user.id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.get("/calendars/events", response_model=List[CalendarEventRead])
async def get_calendar_events(
    start_time: datetime = Query(...),
    end_time: datetime = Query(...),
    limit: int = Query(50, ge=1, le=200),
    current_user: User = Depends(pres_deps.get_current_user),
    get_events_uc=Depends(pres_deps.get_calendar_events_use_case),
):
    """Fetches events from the user's primary CalDAV calendar within the given window."""
    events = await get_events_uc.execute(
        user_id=current_user.id,
        start_time=start_time,
        end_time=end_time,
        limit=limit,
    )
    return [IntegrationPresentationMapper.to_calendar_event_read(e) for e in events]


@router.post("/calendars/events", response_model=CalendarEventRead, status_code=status.HTTP_201_CREATED)
async def create_calendar_event(
    payload: CalendarEventCreate,
    current_user: User = Depends(pres_deps.get_current_user),
    create_event_uc=Depends(pres_deps.get_create_calendar_event_use_case),
):
    """Creates a new event on the user's primary CalDAV calendar."""
    event = await create_event_uc.execute(
        user_id=current_user.id,
        title=payload.title,
        start_time=payload.start_time,
        end_time=payload.end_time,
        description=payload.description or "",
        location=payload.location or "",
        is_all_day=bool(payload.is_all_day),
    )
    return IntegrationPresentationMapper.to_calendar_event_read(event)


@router.put("/calendars/events/{event_id}", response_model=CalendarEventRead)
async def update_calendar_event(
    event_id: str,
    payload: CalendarEventUpdate,
    current_user: User = Depends(pres_deps.get_current_user),
    update_event_uc=Depends(pres_deps.get_update_calendar_event_use_case),
):
    """Updates an existing event on the user's primary CalDAV calendar."""
    event = await update_event_uc.execute(
        user_id=current_user.id,
        event_id=event_id,
        title=payload.title,
        start_time=payload.start_time,
        end_time=payload.end_time,
        description=payload.description,
        location=payload.location,
        is_all_day=payload.is_all_day,
    )
    return IntegrationPresentationMapper.to_calendar_event_read(event)


@router.delete("/calendars/events/{event_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_calendar_event(
    event_id: str,
    current_user: User = Depends(pres_deps.get_current_user),
    del_event_uc=Depends(pres_deps.get_delete_calendar_event_use_case),
):
    """Deletes an event from the user's primary CalDAV calendar."""
    await del_event_uc.execute(user_id=current_user.id, event_id=event_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


# =========================================================================
# SearXNG Web & Academic Search
# =========================================================================
@router.post("/search", response_model=SearchResultResponse)
async def execute_search(
    payload: SearchQueryRequest,
    current_user: User = Depends(pres_deps.get_current_user),
    search_uc=Depends(pres_deps.get_execute_search_use_case),
):
    """Executes a search query with 15-minute caching and live freshness toggle."""
    res = await search_uc.execute(
        query=payload.query,
        fresh=bool(payload.fresh),
        limit=payload.limit or 10,
    )
    return IntegrationPresentationMapper.to_search_result_response(res)


# =========================================================================
# PyMuPDF Document Reader (PDF Upload & Parsing)
# =========================================================================
@router.post("/documents/pdf", response_model=DocumentParseResponse)
async def parse_pdf_document(
    file: UploadFile = File(...),
    max_pages: int = Query(150, ge=1, le=300),
    current_user: User = Depends(pres_deps.get_current_user),
    pdf_uc=Depends(pres_deps.get_parse_pdf_document_use_case),
):
    """Uploads and parses a PDF in-memory, extracting sections, page tags, and citations."""
    chunk_size = 64 * 1024  # 64 KB chunks
    chunks = []
    total_bytes = 0
    try:
        while chunk := await file.read(chunk_size):
            total_bytes += len(chunk)
            if total_bytes > settings.MAX_PDF_SIZE_BYTES:
                mb_limit = settings.MAX_PDF_SIZE_BYTES / (1024 * 1024)
                raise HTTPException(
                    status_code=status.HTTP_413_CONTENT_TOO_LARGE,
                    detail=f"Uploaded file exceeds maximum limit of {mb_limit:.1f} MB.",
                )
            chunks.append(chunk)

        if total_bytes == 0:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Uploaded PDF file is empty.",
            )

        content = b"".join(chunks)
        parsed = await pdf_uc.execute(
            file_bytes=content,
            filename=file.filename or "document.pdf",
            max_pages=max_pages,
        )
        return IntegrationPresentationMapper.to_document_parse_response(parsed)
    finally:
        await file.close()


# =========================================================================
# SQLite Document Storage with Markdown Export
# =========================================================================
@router.get("/documents", response_model=List[DocumentReadResponse])
async def list_documents(
    space_id: Optional[str] = None,
    current_user: User = Depends(pres_deps.get_current_user),
    list_docs_uc=Depends(pres_deps.get_list_documents_use_case),
):
    """Lists saved research notes and documents belonging to the user."""
    docs = await list_docs_uc.execute(user_id=current_user.id, space_id=space_id)
    return [IntegrationPresentationMapper.to_document_read_response(d) for d in docs]


@router.post("/documents", response_model=DocumentReadResponse, status_code=status.HTTP_201_CREATED)
async def save_document(
    payload: DocumentSaveRequest,
    current_user: User = Depends(pres_deps.get_current_user),
    save_doc_uc=Depends(pres_deps.get_save_document_use_case),
):
    """Creates, appends to, or replaces a document with automatic version tracking."""
    doc = await save_doc_uc.execute(
        user_id=current_user.id,
        title=payload.title,
        content=payload.content,
        action=payload.action or "create",
        space_id=payload.space_id,
        format=payload.format or "markdown",
    )
    return IntegrationPresentationMapper.to_document_read_response(doc)


@router.get("/documents/{document_id}", response_model=DocumentReadResponse)
async def get_document(
    document_id: str,
    current_user: User = Depends(pres_deps.get_current_user),
    get_doc_uc=Depends(pres_deps.get_document_use_case),
):
    """Retrieves a document by ID."""
    doc = await get_doc_uc.execute(document_id=document_id, user_id=current_user.id)
    return IntegrationPresentationMapper.to_document_read_response(doc)


@router.get("/documents/{document_id}/export")
async def export_document_as_markdown(
    document_id: str,
    current_user: User = Depends(pres_deps.get_current_user),
    get_doc_uc=Depends(pres_deps.get_document_use_case),
):
    """Exports a document directly as a downloadable .md Markdown file."""
    doc = await get_doc_uc.execute(document_id=document_id, user_id=current_user.id)
    safe_filename = "".join(c for c in doc.title if c.isalnum() or c in (" ", "-", "_")).strip() or "document"
    headers = {"Content-Disposition": f'attachment; filename="{safe_filename}.md"'}
    return Response(content=doc.content, media_type="text/markdown", headers=headers)


@router.delete("/documents/{document_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_document(
    document_id: str,
    current_user: User = Depends(pres_deps.get_current_user),
    del_doc_uc=Depends(pres_deps.get_delete_document_use_case),
):
    """Deletes a document by ID."""
    await del_doc_uc.execute(document_id=document_id, user_id=current_user.id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)
