from typing import Dict, Any
from app.domain.entities.agent import AgentPersonality
from app.presentation.schemas.agent_schemas import AgentRead, AgentTrashRead


class AgentPresentationMapper:
    @staticmethod
    def to_response(entity: AgentPersonality) -> AgentRead:
        return AgentRead(
            id=entity.id,
            slug=entity.slug,
            name=entity.name,
            description=entity.description or "",
            avatar=entity.avatar or "🤖",
            system_prompt=entity.system_prompt,
            llm_model_id=entity.llm_model_id,
            temperature=entity.temperature,
            top_p=entity.top_p,
            tool_permissions=entity.tool_permissions or [],
            owner_id=entity.owner_id,
            is_builtin=entity.is_builtin,
            is_active=entity.is_active,
            deleted_at=entity.deleted_at,
            created_at=entity.created_at,
            updated_at=entity.updated_at,
        )

    @staticmethod
    def to_trash_response(trash_item: Dict[str, Any]) -> AgentTrashRead:
        agent: AgentPersonality = trash_item["agent"]
        read_obj = AgentPresentationMapper.to_response(agent)
        return AgentTrashRead(
            **read_obj.model_dump(),
            days_remaining_in_grace_period=trash_item["days_remaining_in_grace_period"],
        )
