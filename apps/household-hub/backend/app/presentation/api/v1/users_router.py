from typing import List
from fastapi import APIRouter, Depends, status

from app.domain.entities.user import User
from app.presentation.schemas.account_schemas import ChangePinRequest
from app.presentation.schemas.auth_schemas import Token
from app.presentation.schemas.user_schemas import UserRead, UserUpdate
from app.presentation.mappers.auth_presentation_mapper import AuthPresentationMapper
from app.presentation.mappers.user_presentation_mapper import UserPresentationMapper
from app.domain.use_cases.users.change_pin import ChangePinUseCase
from app.domain.use_cases.users.list_members import ListMembersUseCase
from app.domain.use_cases.users.get_member import GetMemberUseCase
from app.domain.use_cases.users.update_profile import UpdateProfileUseCase
from app.domain.use_cases.users.delete_member import DeleteMemberUseCase
from app.presentation.api.deps import (
    get_current_user,
    get_current_admin_user,
    get_change_pin_use_case,
    get_list_members_use_case,
    get_member_use_case,
    get_update_profile_use_case,
    get_delete_member_use_case,
)

router = APIRouter(prefix="/users", tags=["Household Members"])


@router.get("", response_model=List[UserRead])
async def list_members(
    use_case: ListMembersUseCase = Depends(get_list_members_use_case),
    _: User = Depends(get_current_user),
):
    """List all household members (visible to all members)."""
    members = await use_case.execute()
    return [UserPresentationMapper.to_response(u) for u in members]


@router.patch("/me", response_model=UserRead)
async def update_my_profile(
    payload: UserUpdate,
    use_case: UpdateProfileUseCase = Depends(get_update_profile_use_case),
    current_user: User = Depends(get_current_user),
):
    """Authenticated user updates their own name or colour."""
    updated = await use_case.execute(
        current_user=current_user,
        full_name=payload.full_name,
        avatar_color=payload.avatar_color,
    )
    return UserPresentationMapper.to_response(updated)


@router.post("/me/pin", response_model=Token)
async def change_my_pin(
    payload: ChangePinRequest,
    use_case: ChangePinUseCase = Depends(get_change_pin_use_case),
    current_user: User = Depends(get_current_user),
):
    """
    Change your PIN. Every other device is signed out; this one gets the token in the answer.
    A wrong current PIN answers 403 with `attempts_left`, and 429 once it locks, as sign-in does.
    """
    token_dict = await use_case.execute(
        user_id=current_user.id,
        current_pin=payload.current_pin,
        new_pin=payload.new_pin,
    )
    return AuthPresentationMapper.to_token_response(token_dict)


@router.get("/{user_id}", response_model=UserRead)
async def get_member(
    user_id: str,
    use_case: GetMemberUseCase = Depends(get_member_use_case),
    _: User = Depends(get_current_user),
):
    """Get member details by ID."""
    user = await use_case.execute(user_id)
    return UserPresentationMapper.to_response(user)


@router.delete("/{user_id}")
async def delete_member(
    user_id: str,
    use_case: DeleteMemberUseCase = Depends(get_delete_member_use_case),
    current_admin: User = Depends(get_current_admin_user),
):
    """
    Household Admin can delete a member account.
    - Member's personal space and personal memories are permanently purged (Strict Zero-Leak).
    - Authored custom agents are reassigned to an active Household Admin.
    - Cannot delete the only administrator account.
    """
    await use_case.execute(user_id_to_delete=user_id, current_admin=current_admin)
    return {"message": "Member account deleted successfully"}
