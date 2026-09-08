import pytest
from datetime import datetime, timezone, timedelta
from sqlalchemy.ext.asyncio import AsyncSession

from app.domain.entities.gossip_milestone import GossipMilestone
from app.data.models.gossip_model import GossipMilestoneModel
from app.data.mappers.gossip_data_mapper import GossipDataMapper
from app.data.datasources.gossip_data_source import SqliteGossipDataSource
from app.data.repositories.gossip_repository_impl import GossipRepositoryImpl
from app.domain.entities.user import User
from app.data.models.user_model import UserModel
from app.data.models.agent_model import AgentModel


@pytest.mark.asyncio
async def test_gossip_data_mapper():
    mapper = GossipDataMapper()
    now = datetime.now(timezone.utc)
    entity = GossipMilestone(
        id="m-1",
        source_user_id="u-1",
        source_username="alice",
        reporting_agent_id="a-1",
        reporting_agent_name="Assistant",
        target_scope="household",
        category="milestone",
        summary="Studio jury presentation",
        details_json={"date": "2026-10-14"},
        expires_at=now + timedelta(days=5),
        source_session_id="s-1",
        is_active=True,
        created_at=now,
        updated_at=now,
    )

    model = mapper.to_model(entity)
    assert model.id == "m-1"
    assert model.source_user_id == "u-1"
    assert model.source_username == "alice"
    assert model.reporting_agent_id == "a-1"
    assert model.reporting_agent_name == "Assistant"
    assert model.target_scope == "household"
    assert model.category == "milestone"
    assert model.summary == "Studio jury presentation"
    assert model.details_json == {"date": "2026-10-14"}
    assert model.expires_at == entity.expires_at
    assert model.is_active is True

    roundtrip = mapper.to_entity(model)
    assert roundtrip.id == entity.id
    assert roundtrip.summary == entity.summary
    assert roundtrip.source_username == entity.source_username
    assert roundtrip.is_active == entity.is_active


@pytest.mark.asyncio
async def test_gossip_repository_crud_and_expiration(db_session: AsyncSession):
    # Setup user
    user = UserModel(
        id="user-gossip-1",
        username="gossip_alice",
        email="gossip_alice@example.com",
        full_name="Alice Gossip",
        hashed_password="hashed_pw",
    )
    db_session.add(user)
    await db_session.commit()

    data_source = SqliteGossipDataSource(db_session)
    mapper = GossipDataMapper()
    repo = GossipRepositoryImpl(data_source, mapper)

    now = datetime.now(timezone.utc)

    # 1. Publish active milestone
    m1 = GossipMilestone(
        source_user_id="user-gossip-1",
        source_username="gossip_alice",
        target_scope="household",
        category="milestone",
        summary="Alice milestone 1",
        expires_at=now + timedelta(days=2),
    )
    saved_m1 = await repo.publish(m1)
    assert saved_m1.id == m1.id

    # 2. Publish expired milestone
    m2 = GossipMilestone(
        source_user_id="user-gossip-1",
        source_username="gossip_alice",
        target_scope="household",
        category="milestone",
        summary="Alice past milestone",
        expires_at=now - timedelta(hours=1),
    )
    await repo.publish(m2)

    # 3. Query active household milestones (expired should be filtered out)
    active_milestones = await repo.get_active_household_milestones(now=now)
    assert len(active_milestones) == 1
    assert active_milestones[0].id == m1.id
    assert active_milestones[0].summary == "Alice milestone 1"

    # 4. Query user published milestones
    user_milestones = await repo.get_user_published_milestones(user_id="user-gossip-1")
    assert len(user_milestones) == 2

    # 5. Revoke milestone
    revoked = await repo.revoke_milestone(milestone_id=m1.id, user_id="user-gossip-1")
    assert revoked is True

    # Now active household query should be empty
    active_after_revoke = await repo.get_active_household_milestones(now=now)
    assert len(active_after_revoke) == 0

    # 6. Reassign household milestones on member deletion
    # Add another user
    user2 = UserModel(
        id="user-gossip-admin",
        username="gossip_admin",
        email="gossip_admin@example.com",
        full_name="Admin Gossip",
        hashed_password="hashed_pw",
        is_admin=True,
    )
    db_session.add(user2)
    await db_session.commit()

    reassigned_count = await repo.reassign_household_milestones(
        from_user_id="user-gossip-1", to_user_id="user-gossip-admin"
    )
    assert reassigned_count == 2
    admin_milestones = await repo.get_user_published_milestones(user_id="user-gossip-admin")
    assert len(admin_milestones) == 2
