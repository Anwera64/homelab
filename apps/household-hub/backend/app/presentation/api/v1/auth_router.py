from typing import List

from fastapi import APIRouter, Depends, status

from app.domain.entities.user import User
from app.presentation.schemas.auth_schemas import AuthStatus, FirstRunRegister, LoginRequest, MemberProfile, Token
from app.presentation.schemas.pin_reset_schemas import PinResetRedeem
from app.presentation.schemas.user_schemas import UserRead
from app.presentation.mappers.auth_presentation_mapper import AuthPresentationMapper
from app.presentation.mappers.user_presentation_mapper import UserPresentationMapper
from app.domain.use_cases.auth.get_auth_status import GetAuthStatusUseCase
from app.domain.use_cases.auth.list_public_members import ListPublicMembersUseCase
from app.domain.use_cases.auth.register_initial_admin import RegisterInitialAdminUseCase
from app.domain.use_cases.auth.login import LoginUseCase
from app.domain.use_cases.auth.refresh_token import RefreshTokenUseCase
from app.domain.use_cases.auth.redeem_pin_reset import RedeemPinResetUseCase
from app.presentation.api.deps import (
    get_auth_status_use_case,
    get_list_public_members_use_case,
    get_register_initial_admin_use_case,
    get_login_use_case,
    get_refresh_token_use_case,
    get_redeem_pin_reset_use_case,
    get_current_user,
)

router = APIRouter(prefix="/auth", tags=["Authentication"])


@router.get("/status", response_model=AuthStatus)
async def get_auth_status(
    use_case: GetAuthStatusUseCase = Depends(get_auth_status_use_case),
):
    """Check if the household hub has been initialized with an admin user."""
    status_data = await use_case.execute()
    return AuthPresentationMapper.to_status_response(status_data)


@router.get("/members", response_model=List[MemberProfile])
async def list_members(
    use_case: ListPublicMembersUseCase = Depends(get_list_public_members_use_case),
):
    """The profile picker: id, name and colour of every active member. No sign-in needed."""
    members = await use_case.execute()
    return [UserPresentationMapper.to_member_profile(m) for m in members]


@router.post("/register-initial", response_model=Token, status_code=status.HTTP_201_CREATED)
async def register_initial_admin(
    payload: FirstRunRegister,
    use_case: RegisterInitialAdminUseCase = Depends(get_register_initial_admin_use_case),
    login_use_case: LoginUseCase = Depends(get_login_use_case),
):
    """First-run onboarding: registers the Household Admin (only allowed if 0 users exist)."""
    user = await use_case.execute(
        full_name=payload.full_name,
        pin=payload.pin,
        avatar_color=payload.avatar_color,
    )
    token_dict = await login_use_case.execute(user_id=user.id, pin=payload.pin)
    return AuthPresentationMapper.to_token_response(token_dict)


@router.post("/login", response_model=Token)
async def login(
    payload: LoginRequest,
    use_case: LoginUseCase = Depends(get_login_use_case),
):
    """
    Sign in as a member from the picker with their PIN. A wrong PIN answers 401 with
    `attempts_left`; after five, 429 with `retry_after_seconds` until the wait is over.
    """
    token_dict = await use_case.execute(user_id=payload.user_id, pin=payload.pin)
    return AuthPresentationMapper.to_token_response(token_dict)


@router.post("/refresh", response_model=Token)
async def refresh(
    use_case: RefreshTokenUseCase = Depends(get_refresh_token_use_case),
    current_user: User = Depends(get_current_user),
):
    """
    A fresh token for a member still signed in. The token sent must still be accepted: expired,
    issued before the member's token version changed, or for an inactive member answers 401.
    """
    token_dict = await use_case.execute(current_user)
    return AuthPresentationMapper.to_token_response(token_dict)


@router.post("/pin-resets/{code}/redeem", response_model=Token)
async def redeem_pin_reset(
    code: str,
    payload: PinResetRedeem,
    use_case: RedeemPinResetUseCase = Depends(get_redeem_pin_reset_use_case),
):
    """
    Public: whoever forgot their PIN has no token. The code was read out by the member who approved
    it, or issued from the hub itself. It works once, and signs them in with the PIN they choose.
    """
    token_dict = await use_case.execute(code=code, pin=payload.pin)
    return AuthPresentationMapper.to_token_response(token_dict)


@router.get("/me", response_model=UserRead)
async def get_me(current_user: User = Depends(get_current_user)):
    """Retrieve profile of the currently authenticated household member."""
    return UserPresentationMapper.to_response(current_user)
