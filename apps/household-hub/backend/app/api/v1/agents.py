from datetime import datetime, timezone, timedelta
from typing import List
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select

from app.core.config import settings
from app.api.deps import get_db, get_current_user
from app.models.user import User
from app.models.agent import AgentPersonality
from app.schemas.agent import AgentCreate, AgentRead, AgentTrashRead, AgentUpdate

router = APIRouter(prefix="/agents", tags=["Dynamic Agent Catalog"])


@router.get("", response_model=List[AgentRead])
async def list_agents(
    db: AsyncSession = Depends(get_db),
    _: User = Depends(get_current_user),
):
    """List all active agent personalities in the catalog."""
    stmt = (
        select(AgentPersonality)
        .where(AgentPersonality.deleted_at.is_(None))
        .where(AgentPersonality.is_active.is_(True))
        .order_by(AgentPersonality.is_builtin.desc(), AgentPersonality.created_at)
    )
    result = await db.execute(stmt)
    return result.scalars().all()


@router.get("/trash", response_model=List[AgentTrashRead])
async def list_trash(
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """List soft-deleted models within their 7-day undo grace period."""
    stmt = (
        select(AgentPersonality)
        .where(AgentPersonality.deleted_at.is_not(None))
    )
    if not current_user.is_admin:
        stmt = stmt.where(AgentPersonality.owner_id == current_user.id)

    result = await db.execute(stmt)
    agents = result.scalars().all()

    now = datetime.now(timezone.utc)
    trash_items = []
    for a in agents:
        if a.deleted_at:
            # Normalize timezone if needed
            del_at = a.deleted_at if a.deleted_at.tzinfo else a.deleted_at.replace(tzinfo=timezone.utc)
            days_elapsed = (now - del_at).days
            days_remaining = max(0, 7 - days_elapsed)
            if days_elapsed <= 7:
                item_dict = {c.name: getattr(a, c.name) for c in a.__table__.columns}
                item_dict["days_remaining_in_grace_period"] = days_remaining
                trash_items.append(AgentTrashRead(**item_dict))

    return trash_items


@router.delete("/trash/{agent_id}")
async def purge_trash_agent(
    agent_id: str,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Permanently hard-delete a trashed model, immediately freeing its slug.
    Only the model owner or an Admin can purge it.
    """
    result = await db.execute(select(AgentPersonality).where(AgentPersonality.id == agent_id))
    agent = result.scalars().first()
    if not agent or agent.deleted_at is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Trashed model not found")

    if agent.owner_id != current_user.id and not current_user.is_admin:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Only the model owner or an Admin can permanently purge this model.",
        )

    await db.delete(agent)
    await db.commit()
    return {"message": "Model permanently purged from trash. Slug is now available for reuse."}


@router.get("/{id_or_slug}", response_model=AgentRead)
async def get_agent(
    id_or_slug: str,
    db: AsyncSession = Depends(get_db),
    _: User = Depends(get_current_user),
):
    """Get single active agent by ID or slug."""
    stmt = select(AgentPersonality).where(
        ((AgentPersonality.id == id_or_slug) | (AgentPersonality.slug == id_or_slug))
        & AgentPersonality.deleted_at.is_(None)
    )
    result = await db.execute(stmt)
    agent = result.scalars().first()
    if not agent:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Agent personality not found")
    return agent


@router.post("", response_model=AgentRead, status_code=status.HTTP_201_CREATED)
async def create_agent(
    payload: AgentCreate,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Any member can create a custom agent personality. Sets owner_id to current user."""
    # 1. Purge any soft-deleted agents whose 7-day grace period has expired
    cutoff = datetime.now(timezone.utc) - timedelta(days=settings.AGENT_DELETE_GRACE_DAYS)
    expired_stmt = select(AgentPersonality).where(
        AgentPersonality.deleted_at.is_not(None),
        AgentPersonality.deleted_at < cutoff,
    )
    expired_res = await db.execute(expired_stmt)
    for exp in expired_res.scalars().all():
        await db.delete(exp)
    await db.flush()

    # 2. Check if slug exists in active catalog
    existing_active = await db.execute(
        select(AgentPersonality).where(
            AgentPersonality.slug == payload.slug,
            AgentPersonality.deleted_at.is_(None),
        )
    )
    if existing_active.scalars().first():
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"An active agent with slug '{payload.slug}' already exists.",
        )

    # 3. Check if slug exists in trash (under active grace period)
    existing_trash = await db.execute(
        select(AgentPersonality).where(
            AgentPersonality.slug == payload.slug,
            AgentPersonality.deleted_at.is_not(None),
        )
    )
    if existing_trash.scalars().first():
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"An agent with slug '{payload.slug}' is currently in trash (under 7-day grace period). Restore it or purge trash to reuse this slug.",
        )

    agent = AgentPersonality(
        slug=payload.slug,
        name=payload.name,
        description=payload.description or "",
        avatar=payload.avatar or "🤖",
        system_prompt=payload.system_prompt,
        model_alias=payload.model_alias or "qwen3:14b",
        temperature=payload.temperature if payload.temperature is not None else 0.7,
        top_p=payload.top_p if payload.top_p is not None else 0.9,
        tool_permissions=payload.tool_permissions or [],
        owner_id=current_user.id,
        is_builtin=False,
        is_active=True,
    )
    db.add(agent)
    await db.commit()
    await db.refresh(agent)
    return agent


@router.put("/{agent_id}", response_model=AgentRead)
async def update_agent(
    agent_id: str,
    payload: AgentUpdate,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Update agent personality. Only the owner (or Admin for built-ins) can edit."""
    result = await db.execute(select(AgentPersonality).where(AgentPersonality.id == agent_id))
    agent = result.scalars().first()
    if not agent or agent.deleted_at is not None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Agent personality not found")

    if agent.is_builtin:
        if not current_user.is_admin:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Only the Household Admin can modify built-in system agents.",
            )
    else:
        if agent.owner_id != current_user.id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Only the model owner can edit this model.",
            )

    update_data = payload.model_dump(exclude_unset=True)
    for field, val in update_data.items():
        setattr(agent, field, val)

    db.add(agent)
    await db.commit()
    await db.refresh(agent)
    return agent


@router.delete("/{agent_id}")
async def delete_agent(
    agent_id: str,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Soft-delete agent personality with a 7-day undo grace period.
    Only the owner can delete their custom models. Built-in models cannot be deleted.
    """
    result = await db.execute(select(AgentPersonality).where(AgentPersonality.id == agent_id))
    agent = result.scalars().first()
    if not agent or agent.deleted_at is not None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Agent personality not found")

    if agent.is_builtin:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Built-in system models cannot be deleted.",
        )

    if agent.owner_id != current_user.id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Only the model owner can delete this model.",
        )

    agent.deleted_at = datetime.now(timezone.utc)
    db.add(agent)
    await db.commit()

    return {
        "message": "Model soft-deleted. You have a 7-day grace period to undo this deletion.",
        "deleted_at": agent.deleted_at,
        "grace_period_days": 7,
    }


@router.post("/{agent_id}/restore", response_model=AgentRead)
async def restore_agent(
    agent_id: str,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Restore a soft-deleted model within the 7-day grace period."""
    result = await db.execute(select(AgentPersonality).where(AgentPersonality.id == agent_id))
    agent = result.scalars().first()
    if not agent:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Agent personality not found")

    if agent.owner_id != current_user.id and not current_user.is_admin:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Only the model owner can restore this model.",
        )

    if agent.deleted_at is None:
        return agent

    del_at = agent.deleted_at if agent.deleted_at.tzinfo else agent.deleted_at.replace(tzinfo=timezone.utc)
    elapsed_days = (datetime.now(timezone.utc) - del_at).total_seconds() / 86400.0
    if elapsed_days > 7:
        raise HTTPException(
            status_code=status.HTTP_410_GONE,
            detail="Grace period of 7 days has expired. Model cannot be restored.",
        )

    agent.deleted_at = None
    db.add(agent)
    await db.commit()
    await db.refresh(agent)
    return agent
