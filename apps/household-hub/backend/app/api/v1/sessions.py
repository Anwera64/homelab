from typing import List
from fastapi import APIRouter, Depends, HTTPException, status
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
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Get session details and message history.
    Strict Zero-Leak Privacy: Only the session owner can view this session.
    """
    stmt = (
        select(ConversationSession)
        .where(ConversationSession.id == session_id)
        .options(selectinload(ConversationSession.messages))
    )
    result = await db.execute(stmt)
    session = result.scalars().first()
    if not session:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Session not found")

    if session.user_id != current_user.id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Zero-Leak Privacy violation: You cannot access another member's conversation session.",
        )

    return session


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

    message = ChatMessage(
        session_id=session.id,
        role=payload.role,
        content=payload.content,
        metadata_json=payload.metadata_json or {},
    )
    db.add(message)
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
