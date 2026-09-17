from typing import List
from fastapi import APIRouter, Depends, status

from app.domain.entities.user import User
from app.presentation.schemas.account_schemas import ChangePinRequest, LeaveHouseholdRequest
from app.presentation.schemas.pin_reset_schemas import PinResetApprove, PinResetRead
from app.presentation.schemas.auth_schemas import Token
from app.presentation.schemas.user_schemas import UserRead, UserUpdate
from app.presentation.mappers.auth_presentation_mapper import AuthPresentationMapper
from app.presentation.mappers.pin_reset_presentation_mapper import PinResetPresentationMapper
from app.presentation.mappers.user_presentation_mapper import UserPresentationMapper
from app.domain.use_cases.users.change_pin import ChangePinUseCase
from app.domain.use_cases.users.leave_household import LeaveHouseholdUseCase
from app.domain.use_cases.users.approve_pin_reset import ApprovePinResetUseCase
from app.domain.use_cases.users.list_members import ListMembersUseCase
from app.domain.use_cases.users.get_member import GetMemberUseCase
from app.domain.use_cases.users.update_profile import UpdateProfileUseCase
from app.domain.use_cases.users.deactivate_member import DeactivateMemberUseCase
from app.presentation.api.deps import (
    get_current_user,
    get_current_admin_user,
    get_change_pin_use_case,
    get_leave_household_use_case,
    get_approve_pin_reset_use_case,
    get_list_members_use_case,
    get_member_use_case,
    get_update_profile_use_case,
    get_remove_member_use_case,
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


@router.delete("/me")
async def leave_household(
    payload: LeaveHouseholdRequest,
    use_case: LeaveHouseholdUseCase = Depends(get_leave_household_use_case),
    current_user: User = Depends(get_current_user),
):
    """
    Leave the household for good, confirming with your own PIN. Everything private to you is erased
    and what you shared keeps your name on it, exactly as when someone is removed. The only admin
    can't leave, because nothing can promote anyone in their place.
    """
    await use_case.execute(member=current_user, pin=payload.pin)
    return {"message": "You have left the household"}


@router.post("/{user_id}/pin-resets", response_model=PinResetRead, status_code=status.HTTP_201_CREATED)
async def approve_pin_reset(
    user_id: str,
    payload: PinResetApprove,
    use_case: ApprovePinResetUseCase = Depends(get_approve_pin_reset_use_case),
    current_user: User = Depends(get_current_user),
):
    """
    Vouch for a member who has forgotten their PIN: confirm with your own, then read out the code.
    Any member can vouch for any other - there is no mail server, and "the admin resets it" would
    strand the admin. A wrong PIN of your own answers 403, and 429 once it locks.
    """
    reset = await use_case.execute(approver=current_user, target_user_id=user_id, pin=payload.pin)
    return PinResetPresentationMapper.to_response(reset)


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
async def remove_member(
    user_id: str,
    use_case: DeactivateMemberUseCase = Depends(get_remove_member_use_case),
    current_admin: User = Depends(get_current_admin_user),
):
    """
    The admin removes someone else from the household. The account is switched off rather than
    deleted: their name and colour stay, so what they shared keeps their name on it, while their
    chats, personal memories, space, calendar connection, notes and PIN are erased. Agents they made
    pass to the admin, and the phone they are holding stops working.
    """
    await use_case.execute(user_id_to_remove=user_id, current_admin=current_admin)
    return {"message": "Member removed from the household"}
