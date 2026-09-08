from fastapi import APIRouter, Depends

from app.domain.entities.user import User
from app.presentation.schemas.space_schemas import SpaceRead, SpaceSettingsUpdate
from app.presentation.mappers.space_presentation_mapper import SpacePresentationMapper
from app.domain.use_cases.spaces.get_shared_space import GetSharedSpaceUseCase
from app.domain.use_cases.spaces.get_personal_space import GetPersonalSpaceUseCase
from app.domain.use_cases.spaces.get_space_by_id import GetSpaceByIdUseCase
from app.domain.use_cases.spaces.update_personal_settings import UpdatePersonalSettingsUseCase
from app.domain.use_cases.spaces.update_shared_settings import UpdateSharedSettingsUseCase
from app.domain.use_cases.spaces.update_space_settings import UpdateSpaceSettingsUseCase
from app.presentation.api.deps import (
    get_current_user,
    get_shared_space_use_case,
    get_personal_space_use_case,
    get_space_by_id_use_case,
    get_update_personal_settings_use_case,
    get_update_shared_settings_use_case,
    get_update_space_settings_use_case,
)

router = APIRouter(prefix="/spaces", tags=["Spaces Engine"])


@router.get("/shared", response_model=SpaceRead)
async def get_shared_space(
    use_case: GetSharedSpaceUseCase = Depends(get_shared_space_use_case),
    _: User = Depends(get_current_user),
):
    """Retrieve the shared household hub with shared Bento widgets."""
    space = await use_case.execute()
    return SpacePresentationMapper.to_response(space)


@router.get("/personal", response_model=SpaceRead)
async def get_personal_space(
    use_case: GetPersonalSpaceUseCase = Depends(get_personal_space_use_case),
    current_user: User = Depends(get_current_user),
):
    """Retrieve current authenticated member's strictly private personal space."""
    space = await use_case.execute(current_user)
    return SpacePresentationMapper.to_response(space)


@router.get("/{space_id}", response_model=SpaceRead)
async def get_space_by_id(
    space_id: str,
    use_case: GetSpaceByIdUseCase = Depends(get_space_by_id_use_case),
    current_user: User = Depends(get_current_user),
):
    """
    Get space details.
    Zero-Leak Privacy: If space is personal, ONLY the space owner can access it.
    Even admins are forbidden from accessing other members' personal spaces.
    """
    space = await use_case.execute(space_id=space_id, current_user=current_user)
    return SpacePresentationMapper.to_response(space)


@router.put("/personal/settings", response_model=SpaceRead)
async def update_personal_settings(
    payload: SpaceSettingsUpdate,
    use_case: UpdatePersonalSettingsUseCase = Depends(get_update_personal_settings_use_case),
    current_user: User = Depends(get_current_user),
):
    """Update widget layout and dashboard preferences for current user's personal space."""
    space = await use_case.execute(current_user=current_user, settings=payload.settings)
    return SpacePresentationMapper.to_response(space)


@router.put("/shared/settings", response_model=SpaceRead)
async def update_shared_settings(
    payload: SpaceSettingsUpdate,
    use_case: UpdateSharedSettingsUseCase = Depends(get_update_shared_settings_use_case),
    _: User = Depends(get_current_user),
):
    """Update widget layout and dashboard preferences for the shared household hub."""
    space = await use_case.execute(settings=payload.settings)
    return SpacePresentationMapper.to_response(space)


@router.put("/{space_id}/settings", response_model=SpaceRead)
async def update_space_settings(
    space_id: str,
    payload: SpaceSettingsUpdate,
    use_case: UpdateSpaceSettingsUseCase = Depends(get_update_space_settings_use_case),
    current_user: User = Depends(get_current_user),
):
    """Update settings for a specific space with zero-leak ownership check."""
    space = await use_case.execute(space_id=space_id, settings=payload.settings, current_user=current_user)
    return SpacePresentationMapper.to_response(space)
