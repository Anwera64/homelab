from typing import List
from fastapi import APIRouter, Depends, status

from app.domain.entities.user import User
from app.presentation.schemas.user_schemas import UserCreate, UserRead, UserUpdate
from app.presentation.mappers.user_presentation_mapper import UserPresentationMapper
from app.domain.use_cases.users.list_members import ListMembersUseCase
from app.domain.use_cases.users.get_member import GetMemberUseCase
from app.domain.use_cases.users.create_member import CreateMemberUseCase
from app.domain.use_cases.users.update_profile import UpdateProfileUseCase
from app.domain.use_cases.users.delete_member import DeleteMemberUseCase
from app.presentation.api.deps import (
    get_current_user,
    get_current_admin_user,
    get_list_members_use_case,
    get_member_use_case,
    get_create_member_use_case,
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


@router.post("", response_model=UserRead, status_code=status.HTTP_201_CREATED)
async def create_member(
    payload: UserCreate,
    use_case: CreateMemberUseCase = Depends(get_create_member_use_case),
    _: User = Depends(get_current_admin_user),
):
    """Admin creates a new household member account and provisions their personal space."""
    user = await use_case.execute(
        username=payload.username,
        email=payload.email,
        password=payload.password,
        full_name=payload.full_name,
        avatar_color=payload.avatar_color,
        is_admin=bool(payload.is_admin),
    )
    return UserPresentationMapper.to_response(user)


@router.patch("/me", response_model=UserRead)
async def update_my_profile(
    payload: UserUpdate,
    use_case: UpdateProfileUseCase = Depends(get_update_profile_use_case),
    current_user: User = Depends(get_current_user),
):
    """Authenticated user updates their own profile details or password."""
    updated = await use_case.execute(
        current_user=current_user,
        full_name=payload.full_name,
        avatar_color=payload.avatar_color,
        password=payload.password,
    )
    return UserPresentationMapper.to_response(updated)


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
