import asyncio
import json
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, Query, status
from fastapi.responses import StreamingResponse

from app.domain.entities.user import User
from app.presentation.schemas.session_schemas import (
    ChatMessageCreate,
    ChatMessageRead,
    SessionCreate,
    SessionDetailRead,
    SessionRead,
    SessionSecretToggle,
)
from app.presentation.schemas.chat_schemas import (
    ChatTurnRequest,
    ChatTurnResponse,
    ToolApprovalRequest,
    ToolApprovalResponse,
)
from app.presentation.mappers.session_presentation_mapper import SessionPresentationMapper
from app.domain.use_cases.sessions.list_user_sessions import ListUserSessionsUseCase
from app.domain.use_cases.sessions.get_session import GetSessionUseCase
from app.domain.use_cases.sessions.create_session import CreateSessionUseCase
from app.domain.use_cases.sessions.toggle_secret_mode import ToggleSecretModeUseCase
from app.domain.use_cases.sessions.archive_session import ArchiveSessionUseCase
from app.domain.use_cases.sessions.add_chat_message import AddChatMessageUseCase
from app.domain.use_cases.sessions.delete_session import DeleteSessionUseCase
from app.domain.use_cases.agents.get_agent import GetAgentUseCase
from app.domain.use_cases.chat.process_chat_turn import ProcessChatTurnUseCase
from app.presentation.api.session_lock import SessionLockRegistry
from app.presentation.api.deps import (
    get_current_user,
    get_list_user_sessions_use_case,
    get_session_use_case,
    get_create_session_use_case,
    get_toggle_secret_mode_use_case,
    get_archive_session_use_case,
    get_add_chat_message_use_case,
    get_delete_session_use_case,
    get_agent_use_case,
    get_process_chat_turn_use_case,
    get_session_lock_registry,
    get_background_reflection_runner,
    get_background_chat_stream_runner,
)


router = APIRouter(prefix="/sessions", tags=["Conversation Sessions & Secret Mode"])


@router.get("", response_model=List[SessionRead])
async def list_user_sessions(
    use_case: ListUserSessionsUseCase = Depends(get_list_user_sessions_use_case),
    current_user: User = Depends(get_current_user),
):
    """List all private conversation sessions for the current authenticated member."""
    sessions = await use_case.execute(current_user=current_user)
    return [SessionPresentationMapper.to_response(s) for s in sessions]


@router.post("", response_model=SessionRead, status_code=status.HTTP_201_CREATED)
async def create_session(
    payload: SessionCreate,
    use_case: CreateSessionUseCase = Depends(get_create_session_use_case),
    current_user: User = Depends(get_current_user),
):
    """Create a new conversation session with an agent personality."""
    session = await use_case.execute(
        current_user=current_user,
        agent_id=payload.agent_id,
        title=payload.title,
        is_secret=bool(payload.is_secret),
    )
    return SessionPresentationMapper.to_response(session)


@router.get("/{session_id}", response_model=SessionDetailRead)
async def get_session(
    session_id: str,
    limit: int = Query(50, ge=1, le=100),
    before_id: Optional[str] = Query(None),
    use_case: GetSessionUseCase = Depends(get_session_use_case),
    current_user: User = Depends(get_current_user),
):
    """
    Get session details and message history.
    Strict Zero-Leak Privacy: Only the session owner can view this session.
    Supports cursor pagination via 'limit' (1-100) and 'before_id'.
    """
    session, messages = await use_case.execute(
        session_id=session_id,
        current_user=current_user,
        limit=limit,
        before_id=before_id,
    )
    return SessionPresentationMapper.to_detail_response(session, messages)


@router.patch("/{session_id}/secret", response_model=SessionRead)
async def toggle_secret_mode(
    session_id: str,
    payload: SessionSecretToggle,
    use_case: ToggleSecretModeUseCase = Depends(get_toggle_secret_mode_use_case),
    current_user: User = Depends(get_current_user),
):
    """Toggle Zero-Leak Secret Mode on/off for a session."""
    updated = await use_case.execute(
        session_id=session_id,
        is_secret=payload.is_secret,
        current_user=current_user,
    )
    return SessionPresentationMapper.to_response(updated)


@router.post("/{session_id}/messages", response_model=ChatMessageRead, status_code=status.HTTP_201_CREATED)
async def add_message(
    session_id: str,
    payload: ChatMessageCreate,
    use_case: AddChatMessageUseCase = Depends(get_add_chat_message_use_case),
    current_user: User = Depends(get_current_user),
):
    """Append a message to the conversation session."""
    message = await use_case.execute(
        session_id=session_id,
        current_user=current_user,
        role=payload.role,
        content=payload.content,
        metadata_json=payload.metadata_json or {},
    )
    return SessionPresentationMapper.to_message_response(message)


@router.delete("/{session_id}")
async def delete_session(
    session_id: str,
    use_case: DeleteSessionUseCase = Depends(get_delete_session_use_case),
    current_user: User = Depends(get_current_user),
):
    """Delete a conversation session and all its messages."""
    await use_case.execute(session_id=session_id, current_user=current_user)
    return {"message": "Session deleted successfully"}


@router.post("/{session_id}/archive", response_model=SessionRead)
async def archive_session(
    session_id: str,
    use_case: ArchiveSessionUseCase = Depends(get_archive_session_use_case),
    current_user: User = Depends(get_current_user),
):
    """Archive an existing conversation session."""
    session = await use_case.execute(session_id=session_id, current_user=current_user)
    return SessionPresentationMapper.to_response(session)


@router.post("/{session_id}/tools/approve", response_model=ToolApprovalResponse)
async def approve_tool(
    session_id: str,
    payload: ToolApprovalRequest,
    get_session_uc: GetSessionUseCase = Depends(get_session_use_case),
    current_user: User = Depends(get_current_user),
):
    """Approve or reject a tool proposal for an active session."""
    await get_session_uc.execute(session_id=session_id, current_user=current_user)
    status_str = "approved" if payload.approved else "rejected"
    return ToolApprovalResponse(
        status=status_str,
        tool_call_id=payload.tool_call_id,
        result={"status": status_str, "tool_call_id": payload.tool_call_id}
    )


@router.post("/{session_id}/chat", response_model=ChatTurnResponse)
async def chat_turn(
    session_id: str,
    payload: ChatTurnRequest,
    process_use_case: ProcessChatTurnUseCase = Depends(get_process_chat_turn_use_case),
    get_session_uc: GetSessionUseCase = Depends(get_session_use_case),
    agent_uc: GetAgentUseCase = Depends(get_agent_use_case),
    lock_registry: SessionLockRegistry = Depends(get_session_lock_registry),
    reflection_runner = Depends(get_background_reflection_runner),
    current_user: User = Depends(get_current_user),
):
    """
    Execute a full multi-turn conversational inference cycle with autonomous tool execution.
    Guarded by SessionLockRegistry (409 Conflict on concurrent turns).
    Schedules autonomous memory and gossip reflection in the background.
    """
    async with lock_registry.acquire(session_id):
        session_entity, messages = await get_session_uc.execute(session_id=session_id, current_user=current_user)
        is_first_turn = len(messages) == 0
        agent_id = session_entity.agent_id or ""
        agent = await agent_uc.execute(agent_id) if agent_id else None
        agent_name = agent.name if agent else ""

        result = await process_use_case.execute(
            session_id=session_id,
            current_user=current_user,
            content=payload.content,
            auto_approve_writes=payload.auto_approve_writes,
        )

        asyncio.create_task(
            reflection_runner(
                session_id=session_id,
                user_id=current_user.id,
                username=current_user.username,
                agent_id=agent_id,
                agent_name=agent_name,
                user_message=payload.content,
                assistant_message=result.message.content,
                is_secret_session=result.is_secret,
                is_turn_secret=result.is_turn_secret,
                is_first_turn=is_first_turn,
            )
        )

        return ChatTurnResponse(
            message=SessionPresentationMapper.to_message_response(result.message),
            tool_calls=result.tools_executed,
            memories_created_count=0,
            milestones_created_count=0,
            suggest_secret_mode=result.suggest_secret_mode,
            session_title=session_entity.title,
        )


@router.post("/{session_id}/chat/stream")
async def chat_turn_stream(
    session_id: str,
    payload: ChatTurnRequest,
    get_session_uc: GetSessionUseCase = Depends(get_session_use_case),
    agent_uc: GetAgentUseCase = Depends(get_agent_use_case),
    lock_registry: SessionLockRegistry = Depends(get_session_lock_registry),
    stream_runner = Depends(get_background_chat_stream_runner),
    current_user: User = Depends(get_current_user),
):
    """
    Streaming chat endpoint via Server-Sent Events (SSE).
    Uses an asyncio.Queue decoupling bridge and independent AsyncSessionLocal worker:
    in-flight generation runs to completion and saves to database even if the HTTP
    client disconnects early, with immediate 409 Conflict rejection if locked.
    """
    if not await lock_registry.try_acquire(session_id):
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=f"Session {session_id} is currently processing another message.",
        )

    try:
        session_entity, messages = await get_session_uc.execute(session_id=session_id, current_user=current_user)
        is_first_turn = len(messages) == 0
        agent_id = session_entity.agent_id or ""
        agent = await agent_uc.execute(agent_id) if agent_id else None
        agent_name = agent.name if agent else ""
    except Exception:
        await lock_registry.release(session_id)
        raise

    queue = asyncio.Queue()

    async def worker():
        try:
            await stream_runner(
                session_id=session_id,
                current_user=current_user,
                content=payload.content,
                auto_approve_writes=payload.auto_approve_writes,
                queue=queue,
                agent_id=agent_id,
                agent_name=agent_name,
                is_first_turn=is_first_turn,
            )
        finally:
            await lock_registry.release(session_id)

    # Launch background worker
    asyncio.create_task(worker())

    async def sse_generator():
        try:
            while True:
                item = await queue.get()
                if item is None:
                    break
                yield f"data: {json.dumps(item)}\n\n"
            yield "data: [DONE]\n\n"
        except asyncio.CancelledError:
            pass

    return StreamingResponse(sse_generator(), media_type="text/event-stream")


