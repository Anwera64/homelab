from fastapi import APIRouter, Depends, status

from app.domain.entities.user import User
from app.presentation.schemas.invite_schemas import InviteCreate, InviteRead
from app.presentation.mappers.invite_presentation_mapper import InvitePresentationMapper
from app.domain.use_cases.users.create_invite import CreateInviteUseCase
from app.presentation.api.deps import (
    get_current_admin_user,
    get_create_invite_use_case,
)

router = APIRouter(prefix="/invites", tags=["Invites"])


@router.post("", response_model=InviteRead, status_code=status.HTTP_201_CREATED)
async def create_invite(
    payload: InviteCreate,
    use_case: CreateInviteUseCase = Depends(get_create_invite_use_case),
    current_admin: User = Depends(get_current_admin_user),
):
    """Household Admin invites someone by name and gets a one-time code for them to redeem."""
    invite = await use_case.execute(
        inviter=current_admin,
        invited_name=payload.invited_name,
        is_admin=payload.is_admin,
    )
    return InvitePresentationMapper.to_response(invite)
