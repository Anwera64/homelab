from typing import List
from fastapi import APIRouter, Depends, status

from app.domain.entities.user import User
from app.presentation.schemas.agent_schemas import AgentCreate, AgentRead, AgentTrashRead, AgentUpdate
from app.presentation.mappers.agent_presentation_mapper import AgentPresentationMapper
from app.domain.use_cases.agents.list_agents import ListAgentsUseCase
from app.domain.use_cases.agents.get_agent import GetAgentUseCase
from app.domain.use_cases.agents.create_agent import CreateAgentUseCase
from app.domain.use_cases.agents.update_agent import UpdateAgentUseCase
from app.domain.use_cases.agents.soft_delete_agent import SoftDeleteAgentUseCase
from app.domain.use_cases.agents.restore_agent import RestoreAgentUseCase
from app.domain.use_cases.agents.list_trash_agents import ListTrashAgentsUseCase
from app.domain.use_cases.agents.purge_trash_agent import PurgeTrashAgentUseCase
from app.presentation.api.deps import (
    get_current_user,
    get_list_agents_use_case,
    get_agent_use_case,
    get_create_agent_use_case,
    get_update_agent_use_case,
    get_soft_delete_agent_use_case,
    get_restore_agent_use_case,
    get_list_trash_agents_use_case,
    get_purge_trash_agent_use_case,
)

router = APIRouter(prefix="/agents", tags=["Dynamic Agent Catalog"])


@router.get("", response_model=List[AgentRead])
async def list_agents(
    use_case: ListAgentsUseCase = Depends(get_list_agents_use_case),
    _: User = Depends(get_current_user),
):
    """List all active agent personalities in the catalog."""
    agents = await use_case.execute()
    return [AgentPresentationMapper.to_response(a) for a in agents]


@router.get("/trash", response_model=List[AgentTrashRead])
async def list_trash(
    use_case: ListTrashAgentsUseCase = Depends(get_list_trash_agents_use_case),
    current_user: User = Depends(get_current_user),
):
    """List soft-deleted models within their 7-day undo grace period."""
    trash_items = await use_case.execute(current_user=current_user)
    return [AgentPresentationMapper.to_trash_response(item) for item in trash_items]


@router.delete("/trash/{agent_id}")
async def purge_trash_agent(
    agent_id: str,
    use_case: PurgeTrashAgentUseCase = Depends(get_purge_trash_agent_use_case),
    current_user: User = Depends(get_current_user),
):
    """
    Permanently hard-delete a trashed model, immediately freeing its slug.
    Only the model owner or an Admin can purge it.
    """
    await use_case.execute(agent_id=agent_id, current_user=current_user)
    return {"message": "Model permanently purged from trash. Slug is now available for reuse."}


@router.get("/{id_or_slug}", response_model=AgentRead)
async def get_agent(
    id_or_slug: str,
    use_case: GetAgentUseCase = Depends(get_agent_use_case),
    _: User = Depends(get_current_user),
):
    """Get single active agent by ID or slug."""
    agent = await use_case.execute(id_or_slug)
    return AgentPresentationMapper.to_response(agent)


@router.post("", response_model=AgentRead, status_code=status.HTTP_201_CREATED)
async def create_agent(
    payload: AgentCreate,
    use_case: CreateAgentUseCase = Depends(get_create_agent_use_case),
    current_user: User = Depends(get_current_user),
):
    """Any member can create a custom agent personality. Sets owner_id to current user."""
    agent = await use_case.execute(
        current_user=current_user,
        slug=payload.slug,
        name=payload.name,
        system_prompt=payload.system_prompt,
        description=payload.description or "",
        avatar=payload.avatar or "🤖",
        model_alias=payload.model_alias or "qwen3:14b",
        temperature=payload.temperature if payload.temperature is not None else 0.7,
        top_p=payload.top_p if payload.top_p is not None else 0.9,
        tool_permissions=payload.tool_permissions or [],
    )
    return AgentPresentationMapper.to_response(agent)


@router.put("/{agent_id}", response_model=AgentRead)
async def update_agent(
    agent_id: str,
    payload: AgentUpdate,
    use_case: UpdateAgentUseCase = Depends(get_update_agent_use_case),
    current_user: User = Depends(get_current_user),
):
    """Update agent personality. Only the owner (or Admin for built-ins) can edit."""
    updated = await use_case.execute(
        agent_id=agent_id,
        current_user=current_user,
        name=payload.name,
        description=payload.description,
        avatar=payload.avatar,
        system_prompt=payload.system_prompt,
        model_alias=payload.model_alias,
        temperature=payload.temperature,
        top_p=payload.top_p,
        tool_permissions=payload.tool_permissions,
        is_active=payload.is_active,
    )
    return AgentPresentationMapper.to_response(updated)


@router.delete("/{agent_id}")
async def delete_agent(
    agent_id: str,
    use_case: SoftDeleteAgentUseCase = Depends(get_soft_delete_agent_use_case),
    current_user: User = Depends(get_current_user),
):
    """Soft-delete an agent personality into the 7-day trash grace period."""
    agent = await use_case.execute(agent_id=agent_id, current_user=current_user)
    return {
        "message": "Model soft-deleted. You have a 7-day grace period to undo this deletion.",
        "deleted_at": agent.deleted_at,
        "grace_period_days": 7,
    }


@router.post("/{agent_id}/restore", response_model=AgentRead)
async def restore_agent(
    agent_id: str,
    use_case: RestoreAgentUseCase = Depends(get_restore_agent_use_case),
    current_user: User = Depends(get_current_user),
):
    """Restore a soft-deleted model within its 7-day grace period."""
    restored = await use_case.execute(agent_id=agent_id, current_user=current_user)
    return AgentPresentationMapper.to_response(restored)
