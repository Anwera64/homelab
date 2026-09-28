import copy
from typing import Any, Dict

from app.domain.exceptions import ToolFailureReason
from app.domain.repositories.session_repository import ISessionRepository

CALENDAR_TOOLS = ("calendar_read", "calendar_write")

# What a calendar connected just now fixes: a sign-in the calendar refused, or no calendar at all.
FIXED_BY_CONNECTING = (ToolFailureReason.CALENDAR_REJECTED, ToolFailureReason.CALENDAR_NOT_CONNECTED)


class MarkCalendarStepsFixedUseCase:
    """
    A calendar connected: the member's calendar steps that failed for want of one are marked fixed.

    The phone draws a failed step's fix card from what the hub saved with it, so this is what turns
    those cards into "Calendar connected", in every chat, reopened or not. It is never undone: a
    later failure is a new step with a card of its own. Runs inside the caller's unit of work, so the
    calendar and the marks are saved together or not at all.
    """

    def __init__(self, session_repo: ISessionRepository):
        self.session_repo = session_repo

    async def execute(self, user_id: str) -> None:
        answers = await self.session_repo.list_assistant_messages_mentioning(user_id, list(FIXED_BY_CONNECTING))
        for answer in answers:
            metadata = copy.deepcopy(answer.metadata_json or {})
            marked = [part for part in metadata.get("parts") or [] if _fixed_by_connecting(part)]
            if not marked:
                continue
            for part in marked:
                part["summary"]["fixed"] = True
            answer.metadata_json = metadata
            await self.session_repo.update_message(answer)


def _fixed_by_connecting(part: Dict[str, Any]) -> bool:
    summary = part.get("summary")
    return (
        part.get("type") == "tool"
        and part.get("tool") in CALENDAR_TOOLS
        and part.get("success") is False
        and isinstance(summary, dict)
        and summary.get("reason") in FIXED_BY_CONNECTING
        and not summary.get("fixed")
    )
