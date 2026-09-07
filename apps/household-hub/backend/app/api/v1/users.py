from typing import List
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from sqlalchemy.orm import selectinload

from app.api.deps import get_db, get_current_user, get_current_admin_user
from app.core.security import get_password_hash
from app.models.user import User
from app.schemas.user import UserCreate, UserRead
from app.services.spaces_service import create_personal_space

router = APIRouter(prefix="/users", tags=["Household Members"])


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


@router.get("", response_model=List[UserRead])
async def list_members(
    db: AsyncSession = Depends(get_db),
    _: User = Depends(get_current_user),
):
    """List all household members (visible to all members)."""
    stmt = select(User).options(selectinload(User.personal_space)).order_by(User.created_at)
    result = await db.execute(stmt)
    users = result.scalars().all()
    return [build_user_read(u) for u in users]


@router.post("", response_model=UserRead, status_code=status.HTTP_201_CREATED)
async def create_member(
    payload: UserCreate,
    db: AsyncSession = Depends(get_db),
    _: User = Depends(get_current_admin_user),
):
    """Admin creates a new household member account and provisions their personal space."""
    existing = await db.execute(
        select(User).where((User.username == payload.username) | (User.email == payload.email))
    )
    if existing.scalars().first():
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="A user with this username or email already exists.",
        )

    user = User(
        username=payload.username,
        email=payload.email,
        full_name=payload.full_name,
        avatar_color=payload.avatar_color or "#4F46E5",
        hashed_password=get_password_hash(payload.password),
        is_admin=bool(payload.is_admin),
        is_active=True,
    )
    db.add(user)
    await db.flush()

    # Automatically provision personal space for the new member
    await create_personal_space(db, user)
    await db.commit()
    await db.refresh(user, attribute_names=["personal_space"])

    return build_user_read(user)


@router.get("/{user_id}", response_model=UserRead)
async def get_member(
    user_id: str,
    db: AsyncSession = Depends(get_db),
    _: User = Depends(get_current_user),
):
    """Get member details by ID."""
    stmt = select(User).where(User.id == user_id).options(selectinload(User.personal_space))
    result = await db.execute(stmt)
    user = result.scalars().first()
    if not user:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Member not found")
    return build_user_read(user)
