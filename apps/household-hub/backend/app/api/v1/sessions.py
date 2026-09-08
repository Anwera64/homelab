from datetime import datetime, timezone
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, status, Query

from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from sqlalchemy.orm import selectinload

from app.api.deps import get_db, get_current_user
from app.models.user import User
from app.models.agent import AgentPersonality
from app.models.session import ConversationSession, ChatMessage
from app.schemas.session import (
    ChatMessageCreate,
    ChatMessageRead,
    SessionCreate,
    SessionDetailRead,
    SessionRead,
    SessionSecretToggle,
)

router = APIRouter(prefix="/sessions", tags=["Conversation Sessions & Secret Mode"])


@router.get("", response_model=List[SessionRead])
async def list_user_sessions(
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """List all private conversation sessions for the current authenticated member."""
    stmt = (
        select(ConversationSession)
        .where(ConversationSession.user_id == current_user.id)
        .order_by(ConversationSession.updated_at.desc())
    )
    result = await db.execute(stmt)
    return result.scalars().all()


@router.post("", response_model=SessionRead, status_code=status.HTTP_201_CREATED)
async def create_session(
    payload: SessionCreate,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Create a new conversation session with an agent personality."""
    agent_res = await db.execute(
        select(AgentPersonality).where(
            (AgentPersonality.id == payload.agent_id) & AgentPersonality.deleted_at.is_(None)
        )
    )
    agent = agent_res.scalars().first()
    if not agent:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Agent personality not found")

    session = ConversationSession(
        user_id=current_user.id,
        agent_id=payload.agent_id,
        title=payload.title or f"Chat with {agent.name}",
        is_secret=bool(payload.is_secret),
    )
    db.add(session)
    await db.commit()
    await db.refresh(session)
    return session


@router.get("/{session_id}", response_model=SessionDetailRead)
async def get_session(
    session_id: str,
    limit: int = Query(50, ge=1, le=100),
    before_id: Optional[str] = Query(None),
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Get session details and message history.
    Strict Zero-Leak Privacy: Only the session owner can view this session.
    Supports cursor pagination via 'limit' (1-100) and 'before_id'.
    """
    stmt = select(ConversationSession).where(ConversationSession.id == session_id)
    result = await db.execute(stmt)
    session = result.scalars().first()
    if not session:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Session not found")

    if session.user_id != current_user.id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Zero-Leak Privacy violation: You cannot access another member's conversation session.",
        )

    # Message query with cursor pagination
    msg_stmt = select(ChatMessage).where(ChatMessage.session_id == session_id)
    if before_id:
        cursor_res = await db.execute(
            select(ChatMessage.created_at).where(
                (ChatMessage.id == before_id) & (ChatMessage.session_id == session_id)
            )
        )
        cursor_created_at = cursor_res.scalar()
        if cursor_created_at:
            msg_stmt = msg_stmt.where(ChatMessage.created_at < cursor_created_at)

    msg_stmt = msg_stmt.order_by(ChatMessage.created_at.desc()).limit(limit)
    msg_res = await db.execute(msg_stmt)
    messages = list(reversed(msg_res.scalars().all()))

    return SessionDetailRead(
        id=session.id,
        user_id=session.user_id,
        agent_id=session.agent_id,
        title=session.title,
        is_secret=session.is_secret,
        is_archived=session.is_archived,
        created_at=session.created_at,
        updated_at=session.updated_at,
        messages=messages,
    )



@router.patch("/{session_id}/secret", response_model=SessionRead)
async def toggle_secret_mode(
    session_id: str,
    payload: SessionSecretToggle,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Toggle Zero-Leak Secret Mode on/off for a session."""
    result = await db.execute(select(ConversationSession).where(ConversationSession.id == session_id))
    session = result.scalars().first()
    if not session:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Session not found")

    if session.user_id != current_user.id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Zero-Leak Privacy violation: You cannot modify another member's session.",
        )

    session.is_secret = payload.is_secret
    db.add(session)
    await db.commit()
    await db.refresh(session)
    return session


@router.post("/{session_id}/messages", response_model=ChatMessageRead, status_code=status.HTTP_201_CREATED)
async def add_message(
    session_id: str,
    payload: ChatMessageCreate,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Append a message to the conversation session."""
    result = await db.execute(select(ConversationSession).where(ConversationSession.id == session_id))
    session = result.scalars().first()
    if not session:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Session not found")

    if session.user_id != current_user.id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Zero-Leak Privacy violation: You cannot post in another member's session.",
        )

    if session.is_archived or session.agent_id is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Cannot send messages to an archived conversation session.",
        )

    agent_res = await db.execute(
        select(AgentPersonality.deleted_at).where(AgentPersonality.id == session.agent_id)
    )
    agent_deleted_at = agent_res.scalar()
    if agent_deleted_at is not None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Cannot send messages while the agent is in trash. Restore the agent to continue chatting.",
        )


    message = ChatMessage(
        session_id=session.id,
        role=payload.role,
        content=payload.content,
        metadata_json=payload.metadata_json or {},
    )
    session.updated_at = datetime.now(timezone.utc)
    db.add(message)
    db.add(session)
    await db.commit()
    await db.refresh(message)
    return message


@router.delete("/{session_id}")
async def delete_session(
    session_id: str,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Delete a conversation session and all its messages."""
    result = await db.execute(select(ConversationSession).where(ConversationSession.id == session_id))
    session = result.scalars().first()
    if not session:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Session not found")

    if session.user_id != current_user.id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Zero-Leak Privacy violation: You cannot delete another member's session.",
        )

    await db.delete(session)
    await db.commit()
    return {"message": "Session deleted successfully"}
