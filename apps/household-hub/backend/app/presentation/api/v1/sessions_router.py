from typing import List, Optional
from fastapi import APIRouter, Depends, Query, status

from app.domain.entities.user import User
from app.presentation.schemas.session_schemas import (
    ChatMessageCreate,
    ChatMessageRead,
    SessionCreate,
    SessionDetailRead,
    SessionRead,
    SessionSecretToggle,
)
from app.presentation.mappers.session_presentation_mapper import SessionPresentationMapper
from app.domain.use_cases.sessions.list_user_sessions import ListUserSessionsUseCase
from app.domain.use_cases.sessions.get_session import GetSessionUseCase
from app.domain.use_cases.sessions.create_session import CreateSessionUseCase
from app.domain.use_cases.sessions.toggle_secret_mode import ToggleSecretModeUseCase
from app.domain.use_cases.sessions.add_chat_message import AddChatMessageUseCase
from app.domain.use_cases.sessions.delete_session import DeleteSessionUseCase
from app.presentation.api.deps import (
    get_current_user,
    get_list_user_sessions_use_case,
    get_session_use_case,
    get_create_session_use_case,
    get_toggle_secret_mode_use_case,
    get_add_chat_message_use_case,
    get_delete_session_use_case,
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
