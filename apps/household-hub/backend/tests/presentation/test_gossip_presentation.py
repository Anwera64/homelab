from datetime import datetime, timezone
import pytest

from app.domain.entities.gossip_milestone import GossipMilestone
from app.presentation.schemas.gossip_schemas import (
    GossipMilestoneCreateRequest,
    GossipMilestoneResponse,
)
from app.presentation.mappers.gossip_presentation_mapper import GossipPresentationMapper


def test_gossip_presentation_mapper_to_response():
    now = datetime.now(timezone.utc)
    milestone = GossipMilestone(
        id="gm-1",
        source_user_id="u-1",
        source_username="anwera",
        reporting_agent_id="a-1",
        reporting_agent_name="Assistant",
        target_scope="household",
        category="milestone",
        summary="Defended PhD thesis",
        details_json={"grade": "honors"},
        expires_at=None,
        is_active=True,
        created_at=now,
        updated_at=now,
    )

    resp = GossipPresentationMapper.to_response(milestone)
    assert isinstance(resp, GossipMilestoneResponse)
    assert resp.id == "gm-1"
    assert resp.source_username == "anwera"
    assert resp.reporting_agent_name == "Assistant"
    assert resp.summary == "Defended PhD thesis"
    assert resp.details_json == {"grade": "honors"}
    assert resp.is_active is True


def test_gossip_presentation_mapper_to_entity():
    req = GossipMilestoneCreateRequest(
        category="academic_deadline",
        summary="Exam on Monday",
        target_scope="household",
        details_json={"subject": "Math"},
    )

    milestone = GossipPresentationMapper.to_entity(
        request=req,
        user_id="u-2",
        username="maria",
        reporting_agent_id="a-2",
        reporting_agent_name="StudyBuddy",
    )

    assert isinstance(milestone, GossipMilestone)
    assert milestone.source_user_id == "u-2"
    assert milestone.source_username == "maria"
    assert milestone.category == "academic_deadline"
    assert milestone.summary == "Exam on Monday"
    assert milestone.reporting_agent_name == "StudyBuddy"
