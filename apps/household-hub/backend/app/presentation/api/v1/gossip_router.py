from typing import List, Optional
from fastapi import APIRouter, Depends, Query, status

from app.domain.entities.user import User
from app.presentation.schemas.gossip_schemas import (
    GossipMilestoneCreateRequest,
    GossipMilestoneResponse,
)
from app.presentation.mappers.gossip_presentation_mapper import GossipPresentationMapper
from app.domain.use_cases.gossip.publish_gossip_milestone import PublishGossipMilestoneUseCase
from app.domain.use_cases.gossip.manage_gossip_milestones import (
    ListHouseholdMilestonesUseCase,
    ListUserMilestonesAuditUseCase,
    RevokeGossipMilestoneUseCase,
)
from app.presentation.api.deps import (
    get_current_user,
    get_publish_gossip_milestone_use_case,
    get_list_household_milestones_use_case,
    get_list_user_milestones_audit_use_case,
    get_revoke_gossip_milestone_use_case,
)

router = APIRouter(prefix="/gossip", tags=["Gossip Bus & Household Milestones"])


@router.get("/household", response_model=List[GossipMilestoneResponse])
async def list_active_household_milestones(
    limit: int = Query(50, ge=1, le=100),
    use_case: ListHouseholdMilestonesUseCase = Depends(get_list_household_milestones_use_case),
    current_user: User = Depends(get_current_user),
):
    """List all active, non-expired household milestones (cross-agent awareness)."""
    milestones = await use_case.execute(limit=limit)
    return [GossipPresentationMapper.to_response(m) for m in milestones]


@router.get("/audit", response_model=List[GossipMilestoneResponse])
async def list_user_milestones_audit(
    limit: int = Query(50, ge=1, le=100),
    use_case: ListUserMilestonesAuditUseCase = Depends(get_list_user_milestones_audit_use_case),
    current_user: User = Depends(get_current_user),
):
    """Audit view: list all milestones originating from the authenticated member."""
    milestones = await use_case.execute(user_id=current_user.id, limit=limit)
    return [GossipPresentationMapper.to_response(m) for m in milestones]


@router.post("", response_model=GossipMilestoneResponse, status_code=status.HTTP_201_CREATED)
async def publish_milestone(
    payload: GossipMilestoneCreateRequest,
    use_case: PublishGossipMilestoneUseCase = Depends(get_publish_gossip_milestone_use_case),
    current_user: User = Depends(get_current_user),
):
    """Manually publish a milestone to the household gossip bus."""
    published = await use_case.execute(
        source_user_id=current_user.id,
        source_username=current_user.username,
        reporting_agent_id=None,
        reporting_agent_name="Manual User Entry",
        summary=payload.summary,
        category=payload.category,
        target_scope=payload.target_scope,
        details_json=payload.details_json,
        expires_at=payload.expires_at,
    )
    return GossipPresentationMapper.to_response(published)


@router.delete("/{milestone_id}", status_code=status.HTTP_200_OK)
async def revoke_milestone(
    milestone_id: str,
    use_case: RevokeGossipMilestoneUseCase = Depends(get_revoke_gossip_milestone_use_case),
    current_user: User = Depends(get_current_user),
):
    """Revoke an active milestone (user must be owner or admin)."""
    await use_case.execute(milestone_id=milestone_id, current_user=current_user)
    return {"message": "Gossip milestone revoked successfully"}
