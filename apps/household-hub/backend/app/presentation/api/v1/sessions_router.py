import asyncio
import json
from typing import List, Optional
from fastapi import APIRouter, Depends, Header, HTTPException, Query, status
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
from app.presentation.api.turn_log import TurnLog, TurnLogRegistry
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
    get_turn_log_registry,
)


router = APIRouter(prefix="/sessions", tags=["Conversation Sessions & Secret Mode"])


async def _sse(log: TurnLog, after: int = 0):
    """
    A turn as Server-Sent Events, from the event after [after] to the end.

    Each frame's id is `<turn>:<n>`: the number is what a phone that lost the stream asks to resume
    after, and the turn is what stops it resuming into the wrong one. The log belongs to the worker,
    not to this response, so a phone hanging up ends only the listening.
    """
    try:
        async for seq, item in log.follow(after):
            yield f"id: {log.turn_id}:{seq}\ndata: {json.dumps(item)}\n\n"
        yield "data: [DONE]\n\n"
    except asyncio.CancelledError:
        pass


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
    lock_registry: SessionLockRegistry = Depends(get_session_lock_registry),
    current_user: User = Depends(get_current_user),
):
    """
    Get session details and message history.
    Strict Zero-Leak Privacy: Only the session owner can view this session.
    Supports cursor pagination via 'limit' (1-100) and 'before_id'.

    Also reports whether a turn is being generated right now, so a client whose stream dropped can
    tell "still writing" from "the turn died" without waiting for a timeout to decide for it.
    """
    session, messages = await use_case.execute(
        session_id=session_id,
        current_user=current_user,
        limit=limit,
        before_id=before_id,
    )
    detail = SessionPresentationMapper.to_detail_response(session, messages)
    detail.turn_running = lock_registry.is_locked(session_id)
    return detail


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
    x_timezone: Optional[str] = Header(None, alias="X-Timezone"),
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
            timezone_name=x_timezone,
        )

        asyncio.create_task(
            reflection_runner(
                session_id=session_id,
                user_id=current_user.id,
                username=current_user.full_name,
                agent_id=agent_id,
                agent_name=agent_name,
                user_message=payload.content,
                assistant_message=result.message.content,
                is_secret_session=result.is_secret,
                is_turn_secret=result.is_turn_secret,
                is_first_turn=is_first_turn,
                timezone_name=x_timezone,
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


@router.post("/{session_id}/chat/regenerate")
async def regenerate_answer(
    session_id: str,
    get_session_uc: GetSessionUseCase = Depends(get_session_use_case),
    agent_uc: GetAgentUseCase = Depends(get_agent_use_case),
    lock_registry: SessionLockRegistry = Depends(get_session_lock_registry),
    stream_runner = Depends(get_background_chat_stream_runner),
    turn_logs: TurnLogRegistry = Depends(get_turn_log_registry),
    x_timezone: Optional[str] = Header(None, alias="X-Timezone"),
    current_user: User = Depends(get_current_user),
):
    """
    Answer the last question again, streamed like any other turn, without asking it again.

    A turn whose model timed out leaves the question stored and no answer. Re-sending the question
    would store it twice; the screen promises only a fresh answer, so that is what this is.
    """
    if not await lock_registry.try_acquire(session_id):
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=f"Session {session_id} is currently processing another message.",
        )

    try:
        session_entity, messages = await get_session_uc.execute(session_id=session_id, current_user=current_user)

        last_message = messages[-1] if messages else None
        if last_message is None or last_message.role != "user":
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="There is no unanswered question in this conversation to answer again.",
            )

        agent_id = session_entity.agent_id or ""
        agent = await agent_uc.execute(agent_id) if agent_id else None
        agent_name = agent.name if agent else ""
        question = last_message.content
        is_first_turn = len(messages) == 1
    except Exception:
        await lock_registry.release(session_id)
        raise

    log = turn_logs.start(session_id)

    async def worker():
        try:
            await stream_runner(
                session_id=session_id,
                current_user=current_user,
                content=question,
                auto_approve_writes=False,
                queue=log,
                agent_id=agent_id,
                agent_name=agent_name,
                is_first_turn=is_first_turn,
                regenerate=True,
                timezone_name=x_timezone,
            )
        finally:
            await lock_registry.release(session_id)

    turn_logs.spawn(worker())

    return StreamingResponse(_sse(log), media_type="text/event-stream")


@router.post("/{session_id}/chat/stream")
async def chat_turn_stream(
    session_id: str,
    payload: ChatTurnRequest,
    get_session_uc: GetSessionUseCase = Depends(get_session_use_case),
    agent_uc: GetAgentUseCase = Depends(get_agent_use_case),
    lock_registry: SessionLockRegistry = Depends(get_session_lock_registry),
    stream_runner = Depends(get_background_chat_stream_runner),
    turn_logs: TurnLogRegistry = Depends(get_turn_log_registry),
    x_timezone: Optional[str] = Header(None, alias="X-Timezone"),
    current_user: User = Depends(get_current_user),
):
    """
    Streaming chat endpoint via Server-Sent Events (SSE).
    The worker writes to a TurnLog on its own AsyncSessionLocal: in-flight generation runs to
    completion and saves to database even if the HTTP client disconnects early, and a client that
    did can pick the rest up from `GET .../chat/stream`. 409 Conflict immediately if locked.
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

    log = turn_logs.start(session_id)

    async def worker():
        try:
            await stream_runner(
                session_id=session_id,
                current_user=current_user,
                content=payload.content,
                auto_approve_writes=payload.auto_approve_writes,
                queue=log,
                agent_id=agent_id,
                agent_name=agent_name,
                is_first_turn=is_first_turn,
                timezone_name=x_timezone,
            )
        finally:
            await lock_registry.release(session_id)

    turn_logs.spawn(worker())

    return StreamingResponse(_sse(log), media_type="text/event-stream")


@router.get("/{session_id}/chat/stream")
async def resume_turn_stream(
    session_id: str,
    last_event_id: Optional[str] = Query(None),
    last_event_id_header: Optional[str] = Header(None, alias="Last-Event-ID"),
    get_session_uc: GetSessionUseCase = Depends(get_session_use_case),
    turn_logs: TurnLogRegistry = Depends(get_turn_log_registry),
    current_user: User = Depends(get_current_user),
):
    """
    The rest of a turn, for a phone whose stream dropped part way — a locked screen, a lost signal.

    `last_event_id` (or the standard `Last-Event-ID` header) is the last `<turn>:<n>` the phone
    received. Everything after it follows, live if the turn is still being written. 410 Gone means
    the hub no longer holds that turn — long finished, or another has started — and the saved
    message is where the answer is.

    With no id at all — a phone that opened the conversation mid-turn, or was restarted — the turn
    the hub holds is followed from its first event.
    """
    await get_session_uc.execute(session_id=session_id, current_user=current_user)

    log = turn_logs.get(session_id)
    resume_from = last_event_id or last_event_id_header
    if resume_from is None and log is not None:
        return StreamingResponse(_sse(log), media_type="text/event-stream")

    turn_id, _, seq = (resume_from or "").partition(":")
    if log is None or log.turn_id != turn_id or not seq.isdigit():
        raise HTTPException(
            status_code=status.HTTP_410_GONE,
            detail="That turn is no longer being held; read the conversation for its answer.",
        )

    return StreamingResponse(_sse(log, after=int(seq)), media_type="text/event-stream")
