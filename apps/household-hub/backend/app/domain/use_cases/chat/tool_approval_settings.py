from typing import FrozenSet, List, Optional, Tuple

from app.domain.entities.tool_approval import ToolApproval
from app.domain.entities.user import User
from app.domain.exceptions import AlwaysAsksException, InvalidOperationException
from app.domain.repositories.tool_approval_repository import IToolApprovalRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.use_cases.chat.tool_summary import WRITE_ACTIONS


# Actions no setting can make automatic: a removed event or a replaced note can't be undone from
# the chat, so these always show a card.
ALWAYS_ASKS: FrozenSet[Tuple[str, str]] = frozenset({("calendar_write", "delete"), ("document_writer", "replace")})


def can_run_without_asking(tool: str, action: Optional[str], auto: FrozenSet[Tuple[str, str]]) -> bool:
    """Whether a write may run without a card: the member made it automatic, and it may be."""
    return action is not None and (tool, action) in auto and (tool, action) not in ALWAYS_ASKS


async def auto_approved(repo: Optional[IToolApprovalRepository], user_id: str) -> FrozenSet[Tuple[str, str]]:
    """The (tool, action) pairs a member lets agents do without asking. Without settings, none."""
    if repo is None:
        return frozenset()
    return frozenset(
        (saved.tool, saved.action)
        for saved in await repo.list_for_user(user_id)
        if saved.auto and (saved.tool, saved.action) not in ALWAYS_ASKS
    )


async def _every_action(repo: IToolApprovalRepository, user_id: str) -> List[ToolApproval]:
    auto = await auto_approved(repo, user_id)
    return [
        ToolApproval(
            tool=tool,
            action=action,
            auto=(tool, action) in auto,
            always_asks=(tool, action) in ALWAYS_ASKS,
        )
        for tool, actions in WRITE_ACTIONS.items()
        for action in actions
    ]


class ListToolApprovalsUseCase:
    """Every write action agents can take, and whether this member lets them do it without asking."""

    def __init__(self, repo: IToolApprovalRepository):
        self.repo = repo

    async def execute(self, current_user: User) -> List[ToolApproval]:
        return await _every_action(self.repo, current_user.id)


class SetToolApprovalUseCase:
    """
    Turns auto-approve on or off for one of the member's own write actions: from a card's checkbox,
    its Undo, or the settings screen. Answers the whole list, as it now stands.
    """

    def __init__(self, repo: IToolApprovalRepository, uow: IUnitOfWork):
        self.repo = repo
        self.uow = uow

    async def execute(self, current_user: User, tool: str, action: str, auto: bool) -> List[ToolApproval]:
        if action not in WRITE_ACTIONS.get(tool, ()):
            raise InvalidOperationException(f"Agents have no '{action}' action on '{tool}' to approve.")
        if auto and (tool, action) in ALWAYS_ASKS:
            raise AlwaysAsksException("This action always asks first.")
        async with self.uow:
            await self.repo.set(current_user.id, tool, action, auto)
            await self.uow.commit()
        return await _every_action(self.repo, current_user.id)
