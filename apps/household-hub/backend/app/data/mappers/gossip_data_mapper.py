from app.domain.entities.gossip_milestone import GossipMilestone
from app.data.models.gossip_model import GossipMilestoneModel


class GossipDataMapper:
    @staticmethod
    def to_entity(model: GossipMilestoneModel) -> GossipMilestone:
        return GossipMilestone(
            id=model.id,
            source_user_id=model.source_user_id,
            source_username=model.source_username or "",
            reporting_agent_id=model.reporting_agent_id,
            reporting_agent_name=model.reporting_agent_name or "",
            target_scope=model.target_scope,
            category=model.category,
            summary=model.summary,
            details_json=dict(model.details_json or {}),
            expires_at=model.expires_at,
            source_session_id=model.source_session_id,
            is_active=model.is_active,
            created_at=model.created_at,
            updated_at=model.updated_at,
        )

    @staticmethod
    def to_model(entity: GossipMilestone) -> GossipMilestoneModel:
        return GossipMilestoneModel(
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
