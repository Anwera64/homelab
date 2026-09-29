from typing import List, Protocol

from app.domain.entities.tool_approval import ToolApproval


class IToolApprovalRepository(Protocol):
    async def list_for_user(self, user_id: str) -> List[ToolApproval]:
        """The member's saved choices, one per (tool, action) they ever set. Unset ones ask."""
        ...

    async def set(self, user_id: str, tool: str, action: str, auto: bool) -> None:
        ...
