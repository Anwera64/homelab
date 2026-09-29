from app.domain.entities.tool_approval import ToolApproval
from app.presentation.schemas.tool_approval_schemas import ToolApprovalRead


class ToolApprovalPresentationMapper:
    @staticmethod
    def to_response(entity: ToolApproval) -> ToolApprovalRead:
        return ToolApprovalRead(
            tool=entity.tool,
            action=entity.action,
            auto=entity.auto,
            always_asks=entity.always_asks,
        )
