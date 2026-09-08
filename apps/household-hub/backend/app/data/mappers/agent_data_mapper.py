from app.domain.entities.agent import AgentPersonality
from app.data.models.agent_model import AgentModel


class AgentDataMapper:
    @staticmethod
    def to_domain(model: AgentModel) -> AgentPersonality:
        return AgentPersonality(
            id=model.id,
            slug=model.slug,
            name=model.name,
            description=model.description or "",
            avatar=model.avatar or "🤖",
            system_prompt=model.system_prompt,
            model_alias=model.model_alias or "qwen3:14b",
            temperature=model.temperature if model.temperature is not None else 0.7,
            top_p=model.top_p if model.top_p is not None else 0.9,
            tool_permissions=model.tool_permissions or [],
            owner_id=model.owner_id,
            is_builtin=model.is_builtin,
            is_active=model.is_active,
            deleted_at=model.deleted_at,
            created_at=model.created_at,
            updated_at=model.updated_at,
        )

    @staticmethod
    def to_model(entity: AgentPersonality) -> AgentModel:
        return AgentModel(
            id=entity.id,
            slug=entity.slug,
            name=entity.name,
            description=entity.description,
            avatar=entity.avatar,
            system_prompt=entity.system_prompt,
            model_alias=entity.model_alias,
            temperature=entity.temperature,
            top_p=entity.top_p,
            tool_permissions=entity.tool_permissions,
            owner_id=entity.owner_id,
            is_builtin=entity.is_builtin,
            is_active=entity.is_active,
            deleted_at=entity.deleted_at,
            created_at=entity.created_at,
            updated_at=entity.updated_at,
        )
