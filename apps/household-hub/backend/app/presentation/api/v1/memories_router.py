from typing import List, Optional
from fastapi import APIRouter, Depends, status

from app.domain.entities.user import User
from app.presentation.schemas.memory_schemas import MemoryCreate, MemoryRead, MemoryUpdate
from app.presentation.mappers.memory_presentation_mapper import MemoryPresentationMapper
from app.domain.use_cases.memories.list_user_memories import ListUserMemoriesUseCase
from app.domain.use_cases.memories.list_household_memories import ListHouseholdMemoriesUseCase
from app.domain.use_cases.memories.get_memory import GetMemoryUseCase
from app.domain.use_cases.memories.create_memory import CreateMemoryUseCase
from app.domain.use_cases.memories.update_memory import UpdateMemoryUseCase
from app.domain.use_cases.memories.delete_memory import DeleteMemoryUseCase
from app.presentation.api.deps import (
    get_current_user,
    get_list_user_memories_use_case,
    get_list_household_memories_use_case,
    get_memory_use_case,
    get_create_memory_use_case,
    get_update_memory_use_case,
    get_delete_memory_use_case,
)

router = APIRouter(prefix="/memories", tags=["Agent Memories & Relationships"])


@router.get("", response_model=List[MemoryRead])
async def list_user_memories(
    scope: Optional[str] = None,
    agent_id: Optional[str] = None,
    category: Optional[str] = None,
    active_only: bool = True,
    use_case: ListUserMemoriesUseCase = Depends(get_list_user_memories_use_case),
    current_user: User = Depends(get_current_user),
):
    """List personal memories formed about the current authenticated user."""
    memories = await use_case.execute(
        user_id=current_user.id,
        scope=scope,
        agent_id=agent_id,
        category=category,
        active_only=active_only,
    )
    return [MemoryPresentationMapper.to_response(m) for m in memories]


@router.get("/household", response_model=List[MemoryRead])
async def list_household_memories(
    category: Optional[str] = None,
    use_case: ListHouseholdMemoriesUseCase = Depends(get_list_household_memories_use_case),
    _: User = Depends(get_current_user),
):
    """List shared household facts and constraints visible to all members and agents."""
    memories = await use_case.execute(category=category)
    return [MemoryPresentationMapper.to_response(m) for m in memories]


@router.post("", response_model=MemoryRead, status_code=status.HTTP_201_CREATED)
async def create_memory(
    payload: MemoryCreate,
    use_case: CreateMemoryUseCase = Depends(get_create_memory_use_case),
    current_user: User = Depends(get_current_user),
):
    """
    Record or autonomously extract a memory for the current user.
    Enforces that memories from Secret sessions cannot be published to the household scope.
    """
    memory = await use_case.execute(
        user_id=current_user.id,
        content=payload.content,
        scope=payload.scope or "personal",
        category=payload.category or "fact",
        confidence=payload.confidence if payload.confidence is not None else 1.0,
        agent_id=payload.agent_id,
        source_session_id=payload.source_session_id,
    )
    return MemoryPresentationMapper.to_response(memory)


@router.get("/{memory_id}", response_model=MemoryRead)
async def get_memory(
    memory_id: str,
    use_case: GetMemoryUseCase = Depends(get_memory_use_case),
    current_user: User = Depends(get_current_user),
):
    """Retrieve a single memory record with Zero-Leak access control."""
    memory = await use_case.execute(memory_id=memory_id, current_user=current_user)
    return MemoryPresentationMapper.to_response(memory)


@router.put("/{memory_id}", response_model=MemoryRead)
async def update_memory(
    memory_id: str,
    payload: MemoryUpdate,
    use_case: UpdateMemoryUseCase = Depends(get_update_memory_use_case),
    current_user: User = Depends(get_current_user),
):
    """
    Update or refine a memory.
    - Personal memory: Can only be modified by its owner.
    - Household memory: Can be refined by the owner or any Household Admin.
    """
    updated = await use_case.execute(
        memory_id=memory_id,
        current_user=current_user,
        content=payload.content,
        confidence=payload.confidence,
        is_active=payload.is_active,
    )
    return MemoryPresentationMapper.to_response(updated)


@router.delete("/{memory_id}")
async def delete_memory(
    memory_id: str,
    use_case: DeleteMemoryUseCase = Depends(get_delete_memory_use_case),
    current_user: User = Depends(get_current_user),
):
    """
    Permanently revoke and delete an agent memory.
    - Personal memory: Can only be deleted by its owner.
    - Household memory: Can be deleted by the author or any Household Admin.
    """
    await use_case.execute(memory_id=memory_id, current_user=current_user)
    return {"message": "Memory revoked and deleted successfully"}
