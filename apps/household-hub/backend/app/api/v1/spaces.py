from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select

from app.api.deps import get_db, get_current_user
from app.models.user import User
from app.models.space import Space
from app.schemas.space import SpaceRead, SpaceSettingsUpdate
from app.services.spaces_service import get_or_create_shared_space

router = APIRouter(prefix="/spaces", tags=["Spaces Engine"])


@router.get("/shared", response_model=SpaceRead)
async def get_shared_space(
    db: AsyncSession = Depends(get_db),
    _: User = Depends(get_current_user),
):
    """Retrieve the shared household hub with shared Bento widgets."""
    result = await db.execute(select(Space).where(Space.type == "shared"))
    space = result.scalars().first()
    if not space:
        space = await get_or_create_shared_space(db)
        await db.commit()
    return space



@router.get("/personal", response_model=SpaceRead)
async def get_personal_space(
    current_user: User = Depends(get_current_user),
):
    """Retrieve current authenticated member's strictly private personal space."""
    if not current_user.personal_space:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Personal space not found")
    return current_user.personal_space


@router.get("/{space_id}", response_model=SpaceRead)
async def get_space_by_id(
    space_id: str,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Get space details.
    Zero-Leak Privacy: If space is personal, ONLY the space owner can access it.
    Even admins are forbidden from accessing other members' personal spaces.
    """
    result = await db.execute(select(Space).where(Space.id == space_id))
    space = result.scalars().first()
    if not space:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Space not found")

    if space.type == "personal" and space.owner_id != current_user.id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Zero-Leak Privacy violation: Personal spaces are strictly private to their owner.",
        )

    return space


@router.put("/personal/settings", response_model=SpaceRead)
async def update_personal_settings(
    payload: SpaceSettingsUpdate,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Update widget layout and dashboard preferences for current user's personal space."""
    space = current_user.personal_space
    if not space:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Personal space not found")

    space.settings = payload.settings
    db.add(space)
    await db.commit()
    await db.refresh(space)
    return space


@router.put("/shared/settings", response_model=SpaceRead)
async def update_shared_settings(
    payload: SpaceSettingsUpdate,
    db: AsyncSession = Depends(get_db),
    _: User = Depends(get_current_user),
):
    """Update widget layout and dashboard preferences for the shared household hub."""
    space = await get_or_create_shared_space(db)
    space.settings = payload.settings
    db.add(space)
    await db.commit()
    await db.refresh(space)
    return space


@router.put("/{space_id}/settings", response_model=SpaceRead)
async def update_space_settings(
    space_id: str,
    payload: SpaceSettingsUpdate,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Update settings for a specific space with zero-leak ownership check."""
    result = await db.execute(select(Space).where(Space.id == space_id))
    space = result.scalars().first()
    if not space:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Space not found")

    if space.type == "personal" and space.owner_id != current_user.id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Zero-Leak Privacy violation: Cannot modify another member's personal space.",
        )

    space.settings = payload.settings
    db.add(space)
    await db.commit()
    await db.refresh(space)
    return space
