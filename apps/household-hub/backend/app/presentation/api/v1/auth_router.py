from fastapi import APIRouter, Depends, status

from app.domain.entities.user import User
from app.presentation.schemas.auth_schemas import AuthStatus, FirstRunRegister, LoginRequest, Token
from app.presentation.schemas.user_schemas import UserRead
from app.presentation.mappers.auth_presentation_mapper import AuthPresentationMapper
from app.presentation.mappers.user_presentation_mapper import UserPresentationMapper
from app.domain.use_cases.auth.get_auth_status import GetAuthStatusUseCase
from app.domain.use_cases.auth.register_initial_admin import RegisterInitialAdminUseCase
from app.domain.use_cases.auth.login import LoginUseCase
from app.presentation.api.deps import (
    get_auth_status_use_case,
    get_register_initial_admin_use_case,
    get_login_use_case,
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


@router.post("/register-initial", response_model=Token, status_code=status.HTTP_201_CREATED)
async def register_initial_admin(
    payload: FirstRunRegister,
    use_case: RegisterInitialAdminUseCase = Depends(get_register_initial_admin_use_case),
    login_use_case: LoginUseCase = Depends(get_login_use_case),
):
    """First-run onboarding: registers the Household Admin (only allowed if 0 users exist)."""
    user = await use_case.execute(
        username=payload.username,
        email=payload.email,
        password=payload.password,
        full_name=payload.full_name,
        avatar_color=payload.avatar_color,
    )
    # Generate token by logging in newly registered admin
    token_dict = await login_use_case.execute(username=payload.username, password=payload.password)
    return AuthPresentationMapper.to_token_response(token_dict)


@router.post("/login", response_model=Token)
async def login(
    payload: LoginRequest,
    use_case: LoginUseCase = Depends(get_login_use_case),
):
    """Authenticate with username and password, returns JWT token."""
    token_dict = await use_case.execute(username=payload.username, password=payload.password)
    return AuthPresentationMapper.to_token_response(token_dict)


@router.get("/me", response_model=UserRead)
async def get_me(current_user: User = Depends(get_current_user)):
    """Retrieve profile of the currently authenticated household member."""
    return UserPresentationMapper.to_response(current_user)
