from typing import Optional
from app.domain.entities.gossip_milestone import GossipMilestone
from app.presentation.schemas.gossip_schemas import (
    GossipMilestoneCreateRequest,
    GossipMilestoneResponse,
)


class GossipPresentationMapper:
    @staticmethod
    def to_response(entity: GossipMilestone) -> GossipMilestoneResponse:
        return GossipMilestoneResponse(
            id=entity.id,
            source_user_id=entity.source_user_id,
            source_username=entity.source_username,
            reporting_agent_id=entity.reporting_agent_id,
            reporting_agent_name=entity.reporting_agent_name,
            target_scope=entity.target_scope,
            category=entity.category,
            summary=entity.summary,
            details_json=entity.details_json,
            expires_at=entity.expires_at,
            source_session_id=entity.source_session_id,
            is_active=entity.is_active,
            created_at=entity.created_at,
            updated_at=entity.updated_at,
        )

    @staticmethod
    def to_entity(
        request: GossipMilestoneCreateRequest,
        user_id: str,
        username: str,
        reporting_agent_id: Optional[str] = None,
        reporting_agent_name: str = "",
    ) -> GossipMilestone:
        return GossipMilestone(
            source_user_id=user_id,
            source_username=username,
            reporting_agent_id=reporting_agent_id,
            reporting_agent_name=reporting_agent_name,
            target_scope=request.target_scope,
            category=request.category,
            summary=request.summary,
            details_json=request.details_json or {},
            expires_at=request.expires_at,
        )
