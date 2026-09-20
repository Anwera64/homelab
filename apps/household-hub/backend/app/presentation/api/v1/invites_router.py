from fastapi import APIRouter, Depends, status

from app.domain.entities.user import User
from app.presentation.schemas.auth_schemas import Token
from app.presentation.schemas.invite_schemas import InviteCreate, InvitePreviewRead, InviteRead, InviteRedeem
from app.presentation.mappers.auth_presentation_mapper import AuthPresentationMapper
from app.presentation.mappers.invite_presentation_mapper import InvitePresentationMapper
from app.domain.use_cases.auth.look_up_invite import LookUpInviteUseCase
from app.domain.use_cases.auth.redeem_invite import RedeemInviteUseCase
from app.domain.use_cases.users.create_invite import CreateInviteUseCase
from app.presentation.api.deps import (
    get_current_admin_user,
    get_create_invite_use_case,
    get_look_up_invite_use_case,
    get_redeem_invite_use_case,
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


@router.get("/{code}", response_model=InvitePreviewRead)
async def look_up_invite(
    code: str,
    use_case: LookUpInviteUseCase = Depends(get_look_up_invite_use_case),
):
    """Public. Before choosing a PIN, the joiner sees who invited them and the name they were invited as."""
    preview = await use_case.execute(code)
    return InvitePresentationMapper.to_preview_response(preview)


@router.post("/{code}/redeem", response_model=Token, status_code=status.HTTP_201_CREATED)
async def redeem_invite(
    code: str,
    payload: InviteRedeem,
    use_case: RedeemInviteUseCase = Depends(get_redeem_invite_use_case),
):
    """Public. The joiner redeems their code with the name, PIN and colour they chose, and is signed in."""
    token_data = await use_case.execute(
        code=code,
        full_name=payload.full_name,
        pin=payload.pin,
        avatar_color=payload.avatar_color,
    )
    return AuthPresentationMapper.to_token_response(token_data)
