from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, status, Query
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select

from app.api.deps import get_db, get_current_user
from app.models.user import User
from app.models.session import ConversationSession
from app.models.memory import AgentMemory
from app.schemas.memory import MemoryCreate, MemoryRead, MemoryUpdate

router = APIRouter(prefix="/memories", tags=["Agent Memories & Relationships"])


@router.get("", response_model=List[MemoryRead])
async def list_user_memories(
    scope: Optional[str] = None,
    agent_id: Optional[str] = None,
    category: Optional[str] = None,
    active_only: bool = True,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """List personal memories formed about the current authenticated user."""
    stmt = select(AgentMemory).where(AgentMemory.user_id == current_user.id)
    if active_only:
        stmt = stmt.where(AgentMemory.is_active.is_(True))
    if scope:
        stmt = stmt.where(AgentMemory.scope == scope)
    if agent_id:
        stmt = stmt.where(AgentMemory.agent_id == agent_id)
    if category:
        stmt = stmt.where(AgentMemory.category == category)

    stmt = stmt.order_by(AgentMemory.created_at.desc())
    result = await db.execute(stmt)
    return result.scalars().all()


@router.get("/household", response_model=List[MemoryRead])
async def list_household_memories(
    category: Optional[str] = None,
    db: AsyncSession = Depends(get_db),
    _: User = Depends(get_current_user),
):
    """List shared household facts and constraints visible to all members and agents."""
    stmt = (
        select(AgentMemory)
        .where(AgentMemory.scope == "household")
        .where(AgentMemory.is_active.is_(True))
    )
    if category:
        stmt = stmt.where(AgentMemory.category == category)

    stmt = stmt.order_by(AgentMemory.created_at.desc())
    result = await db.execute(stmt)
    return result.scalars().all()


@router.post("", response_model=MemoryRead, status_code=status.HTTP_201_CREATED)
async def create_memory(
    payload: MemoryCreate,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Record or autonomously extract a memory for the current user.
    Enforces that memories from Secret sessions cannot be published to the household scope.
    """
    if payload.scope not in ["personal", "household"]:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Scope must be either 'personal' or 'household'.",
        )

    if payload.source_session_id:
        sess_res = await db.execute(
            select(ConversationSession).where(ConversationSession.id == payload.source_session_id)
        )
        session = sess_res.scalars().first()
        if not session:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Source session not found")

        if session.user_id != current_user.id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Zero-Leak Privacy violation: Cannot link memory to another member's session.",
            )

        if session.is_secret and payload.scope == "household":
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Cannot publish household memory from a secret session. Zero-Leak confidentiality enforced.",
            )

    memory = AgentMemory(
        user_id=current_user.id,
        agent_id=payload.agent_id,
        scope=payload.scope or "personal",
        category=payload.category or "fact",
        content=payload.content,
        confidence=payload.confidence if payload.confidence is not None else 1.0,
        source_session_id=payload.source_session_id,
        is_active=True,
    )
    db.add(memory)
    await db.commit()
    await db.refresh(memory)
    return memory


@router.get("/{memory_id}", response_model=MemoryRead)
async def get_memory(
    memory_id: str,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Retrieve a single memory record with Zero-Leak access control."""
    result = await db.execute(select(AgentMemory).where(AgentMemory.id == memory_id))
    memory = result.scalars().first()
    if not memory:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Memory not found")

    if memory.scope == "personal" and memory.user_id != current_user.id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Zero-Leak Privacy violation: You cannot access another member's personal memory.",
        )

    return memory


@router.put("/{memory_id}", response_model=MemoryRead)
async def update_memory(
    memory_id: str,
    payload: MemoryUpdate,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    User can edit or refine any memory an agent has formed.
    For household-scoped memories, Household Admins can also edit them.
    """
    result = await db.execute(select(AgentMemory).where(AgentMemory.id == memory_id))
    memory = result.scalars().first()
    if not memory:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Memory not found")

    can_edit = (memory.user_id == current_user.id) or (memory.scope == "household" and current_user.is_admin)
    if not can_edit:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Only the memory owner (or a Household Admin for shared memories) can edit this memory.",
        )

    update_data = payload.model_dump(exclude_unset=True)
    for field, val in update_data.items():
        setattr(memory, field, val)

    db.add(memory)
    await db.commit()
    await db.refresh(memory)
    return memory


@router.delete("/{memory_id}")
async def delete_memory(
    memory_id: str,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    User can revoke or permanently delete any memory an agent has formed.
    For household-scoped memories, Household Admins can also revoke them.
    """
    result = await db.execute(select(AgentMemory).where(AgentMemory.id == memory_id))
    memory = result.scalars().first()
    if not memory:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Memory not found")

    can_delete = (memory.user_id == current_user.id) or (memory.scope == "household" and current_user.is_admin)
    if not can_delete:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Only the memory owner (or a Household Admin for shared memories) can delete this memory.",
        )

    await db.delete(memory)
    await db.commit()
    return {"message": "Memory revoked and deleted successfully"}
