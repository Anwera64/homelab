from app.domain.entities.tool_approval import ToolApproval
from app.data.models.tool_approval_model import ToolApprovalModel


class ToolApprovalDataMapper:
    @staticmethod
    def to_domain(model: ToolApprovalModel) -> ToolApproval:
        return ToolApproval(tool=model.tool, action=model.action, auto=bool(model.auto))
