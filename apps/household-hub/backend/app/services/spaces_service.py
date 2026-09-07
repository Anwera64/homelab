from typing import Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select

from app.models.user import User
from app.models.space import Space


DEFAULT_SHARED_SETTINGS = {
    "layout_version": 1,
    "columns": 4,
    "widgets": [
        {
            "id": "w-shared-cal",
            "type": "calendar",
            "title": "Household Schedule",
            "size": "large",
            "position": 0,
            "config": {"view": "week", "days_ahead": 7},
        },
        {
            "id": "w-shared-launcher",
            "type": "agent_launcher",
            "title": "Household AI Assistants",
            "size": "medium",
            "position": 1,
            "config": {"pinned_agents": ["assistant", "researcher"]},
        },
    ],
}

DEFAULT_PERSONAL_SETTINGS = {
    "layout_version": 1,
    "columns": 2,
    "widgets": [
        {
            "id": "w-personal-agenda",
            "type": "agenda",
            "title": "My Agenda",
            "size": "medium",
            "position": 0,
            "config": {"days_ahead": 3},
        },
        {
            "id": "w-personal-recent",
            "type": "recent_sessions",
            "title": "Recent Chats",
            "size": "medium",
            "position": 1,
            "config": {"limit": 5},
        },
    ],
}


async def get_or_create_shared_space(db: AsyncSession) -> Space:
    """Retrieve the singleton shared household space or create it if not present."""
    result = await db.execute(select(Space).where(Space.type == "shared"))
    space = result.scalars().first()
    if not space:
        space = Space(
            name="Household Shared Hub",
            type="shared",
            owner_id=None,
            settings=DEFAULT_SHARED_SETTINGS,
        )
        db.add(space)
        await db.flush()
    return space


async def create_personal_space(db: AsyncSession, user: User) -> Space:
    """Provision a private personal space for a household user."""
    space = Space(
        name=f"{user.full_name}'s Space",
        type="personal",
        owner_id=user.id,
        settings=DEFAULT_PERSONAL_SETTINGS,
    )
    db.add(space)
    await db.flush()
    return space
