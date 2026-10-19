from typing import List, Optional
from fastapi import APIRouter, Depends, Response, status

from app.domain.entities.user import User
from app.presentation.schemas.memory_schemas import (
    ChangesToCheckRead,
    MemoryCreate,
    MemoryPageRead,
    MemoryRead,
    MemoryUpdate,
    ProposalAnswer,
    ProposalRead,
)
from app.domain.use_cases.memories.answer_proposal import AnswerProposalUseCase, UndoProposalAnswerUseCase
from app.domain.use_cases.memories.changes_to_check import ListProposalsUseCase
from app.presentation.mappers.memory_presentation_mapper import MemoryPresentationMapper
from app.domain.use_cases.memories.list_user_memories import ListUserMemoriesUseCase
from app.domain.use_cases.memories.list_household_memories import ListHouseholdMemoriesUseCase
from app.domain.use_cases.memories.get_memory import GetMemoryUseCase
from app.domain.use_cases.memories.create_memory import CreateMemoryUseCase
from app.domain.use_cases.memories.update_memory import UpdateMemoryUseCase
from app.domain.use_cases.memories.list_memory_page import ListMemoryPageUseCase
from app.domain.use_cases.memories.restore_memory import RestoreMemoryUseCase
from app.domain.use_cases.memories.retire_memory import RetireMemoryUseCase
from app.presentation.api.deps import (
    get_current_user,
    get_list_user_memories_use_case,
    get_list_household_memories_use_case,
    get_memory_use_case,
    get_create_memory_use_case,
    get_update_memory_use_case,
    get_list_memory_page_use_case,
    get_restore_memory_use_case,
    get_retire_memory_use_case,
    get_list_proposals_use_case,
    get_answer_proposal_use_case,
    get_undo_proposal_answer_use_case,
    get_withdraw_change_use_case,
    get_restore_change_use_case,
)
from app.domain.use_cases.memories.withdraw_change import RestoreChangeUseCase, WithdrawChangeUseCase

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


@router.get("/page", response_model=MemoryPageRead)
async def memory_page(
    use_case: ListMemoryPageUseCase = Depends(get_list_memory_page_use_case),
    current_user: User = Depends(get_current_user),
):
    """The memory page: your personal memories, and every household memory with who said it."""
    return MemoryPresentationMapper.to_page(await use_case.execute(current_user))


@router.get("/proposals", response_model=ChangesToCheckRead)
async def list_proposals(
    use_case: ListProposalsUseCase = Depends(get_list_proposals_use_case),
    current_user: User = Depends(get_current_user),
):
    """Changes to check: yours to answer, then the ones from your chats waiting for someone else."""
    return MemoryPresentationMapper.to_changes(await use_case.execute(current_user))


@router.post("/proposals/{proposal_id}/answer", response_model=ProposalRead)
async def answer_proposal(
    proposal_id: str,
    payload: ProposalAnswer,
    use_case: AnswerProposalUseCase = Depends(get_answer_proposal_use_case),
    current_user: User = Depends(get_current_user),
):
    """Keep stored, Keep both or Use new. Only the member who said the stored memory may answer."""
    return MemoryPresentationMapper.to_proposal(await use_case.execute(proposal_id, payload.answer, current_user))


@router.post("/proposals/{proposal_id}/undo", response_model=ProposalRead)
async def undo_proposal_answer(
    proposal_id: str,
    use_case: UndoProposalAnswerUseCase = Depends(get_undo_proposal_answer_use_case),
    current_user: User = Depends(get_current_user),
):
    """Undo after answering: the change is open again and everything is as before."""
    return MemoryPresentationMapper.to_proposal(await use_case.execute(proposal_id, current_user))


@router.delete("/proposals/{proposal_id}", response_model=ProposalRead)
async def withdraw_change(
    proposal_id: str,
    use_case: WithdrawChangeUseCase = Depends(get_withdraw_change_use_case),
    current_user: User = Depends(get_current_user),
):
    """✕ on a waiting card: take back a sentence you asked to remember, before it was answered."""
    return MemoryPresentationMapper.to_proposal(await use_case.execute(proposal_id, current_user))


@router.post("/proposals/{proposal_id}/restore", response_model=ProposalRead)
async def restore_change(
    proposal_id: str,
    use_case: RestoreChangeUseCase = Depends(get_restore_change_use_case),
    current_user: User = Depends(get_current_user),
):
    """Undo after ✕ on a waiting card: it waits again, if the memory it waits on is still there."""
    return MemoryPresentationMapper.to_proposal(await use_case.execute(proposal_id, current_user))


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


@router.delete("/{memory_id}", status_code=status.HTTP_204_NO_CONTENT)
async def remove_memory(
    memory_id: str,
    use_case: RetireMemoryUseCase = Depends(get_retire_memory_use_case),
    current_user: User = Depends(get_current_user),
):
    """✕ on the memory page. The memory is retired, not deleted, so it can be restored. Only its own member may."""
    await use_case.execute(memory_id=memory_id, current_user=current_user)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post("/{memory_id}/restore", response_model=MemoryRead)
async def restore_memory(
    memory_id: str,
    use_case: RestoreMemoryUseCase = Depends(get_restore_memory_use_case),
    current_user: User = Depends(get_current_user),
):
    """Undo after ✕: the member's removed memory is active again."""
    memory = await use_case.execute(memory_id=memory_id, current_user=current_user)
    return MemoryPresentationMapper.to_response(memory)
