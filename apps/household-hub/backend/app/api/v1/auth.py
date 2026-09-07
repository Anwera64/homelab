from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func
from sqlalchemy.orm import selectinload

from app.api.deps import get_db, get_current_user
from app.core.security import verify_password, get_password_hash, create_access_token
from app.models.user import User
from app.schemas.auth import AuthStatus, FirstRunRegister, LoginRequest, Token
from app.schemas.user import UserRead
from app.services.spaces_service import create_personal_space, get_or_create_shared_space

router = APIRouter(prefix="/auth", tags=["Authentication"])


def build_user_read(user: User) -> UserRead:
    return UserRead(
        id=user.id,
        username=user.username,
        email=user.email,
        full_name=user.full_name,
        avatar_color=user.avatar_color,
        is_admin=user.is_admin,
        is_active=user.is_active,
        personal_space_id=user.personal_space.id if user.personal_space else None,
        created_at=user.created_at,
    )


@router.get("/status", response_model=AuthStatus)
async def get_auth_status(db: AsyncSession = Depends(get_db)):
    """Check if the household hub has been initialized with an admin user."""
    count_res = await db.execute(select(func.count(User.id)))
    count = count_res.scalar() or 0
    return AuthStatus(is_initialized=(count > 0), member_count=count)


@router.post("/register-initial", response_model=Token, status_code=status.HTTP_201_CREATED)
async def register_initial_admin(
    payload: FirstRunRegister,
    db: AsyncSession = Depends(get_db),
):
    """First-run onboarding: registers the Household Admin (only allowed if 0 users exist)."""
    count_res = await db.execute(select(func.count(User.id)))
    if (count_res.scalar() or 0) > 0:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="System is already initialized. New members must be added by an admin.",
        )

    # Check email/username
    existing = await db.execute(
        select(User).where((User.username == payload.username) | (User.email == payload.email))
    )
    if existing.scalars().first():
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="A user with this username or email already exists.",
        )

    # Create admin user
    user = User(
        username=payload.username,
        email=payload.email,
        full_name=payload.full_name,
        avatar_color=payload.avatar_color or "#4F46E5",
        hashed_password=get_password_hash(payload.password),
        is_admin=True,
        is_active=True,
    )
    db.add(user)
    await db.flush()

    # Automatically provision personal space & shared space
    personal_space = await create_personal_space(db, user)
    await get_or_create_shared_space(db)
    await db.commit()
    await db.refresh(user, attribute_names=["personal_space"])

    # Issue JWT token
    token = create_access_token(subject=user.id, is_admin=user.is_admin)
    return Token(
        access_token=token,
        token_type="bearer",
        user=build_user_read(user),
    )


@router.post("/login", response_model=Token)
async def login(
    payload: LoginRequest,
    db: AsyncSession = Depends(get_db),
):
    """Authenticate with username and password, returns JWT token."""
    stmt = select(User).where(User.username == payload.username).options(selectinload(User.personal_space))
    result = await db.execute(stmt)
    user = result.scalars().first()

    if not user or not verify_password(payload.password, user.hashed_password):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid username or password",
            headers={"WWW-Authenticate": "Bearer"},
        )

    if not user.is_active:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Inactive user account",
        )

    token = create_access_token(subject=user.id, is_admin=user.is_admin)
    return Token(
        access_token=token,
        token_type="bearer",
        user=build_user_read(user),
    )


@router.get("/me", response_model=UserRead)
async def get_me(current_user: User = Depends(get_current_user)):
    """Retrieve profile of the currently authenticated household member."""
    return build_user_read(current_user)
