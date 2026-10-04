from datetime import datetime, tzinfo
from typing import Any, Dict, FrozenSet, List, Optional, Tuple

from app.domain.entities.calendar_event import ALL, CalendarEvent
from app.domain.entities.llm_message import LLMMessage, LLMToolCall
from app.domain.entities.session import PAUSED_TURN, ChatMessage
from app.domain.exceptions import AlreadyDecidedException, ApprovalPendingException, EntityNotFoundException
from app.domain.repositories.session_repository import ISessionRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.use_cases.chat.current_date_line import zone_of
from app.domain.use_cases.chat.tool_approval_settings import can_run_without_asking
from app.domain.use_cases.chat.tool_summary import WRITE_ACTIONS, write_action


# What the model reads for a write the member said no to.
DECLINED = "The member declined this action. It was not done; don't try it again unless they ask."

# What the model reads, beside the result, for a write the member changed on its card.
MEMBER_EDITED = (
    "The member changed these details on the card before approving. The action was done with the "
    "member's values, not the ones you proposed. Use the member's values in your answer."
)


def needs_asking(call: LLMToolCall, auto: FrozenSet[Tuple[str, str]], is_turn_secret: bool) -> bool:
    """
    A write asks the member first, unless they made that action automatic ([auto]). A secret turn
    never asks: writes are refused in secret mode, so a card would only ask for something that
    cannot happen.
    """
    if call.name not in WRITE_ACTIONS or is_turn_secret:
        return False
    return not can_run_without_asking(call.name, write_action(call.name, call.arguments), auto)


def proposal_part(call: LLMToolCall) -> Dict[str, Any]:
    return {
        "type": "proposal",
        "tool_call_id": call.id,
        "tool": call.name,
        "action": write_action(call.name, call.arguments),
        "arguments": call.arguments,
        "status": "pending",
    }


def member_changes(proposed: Dict[str, Any], modified: Dict[str, Any]) -> Dict[str, Any]:
    """The details the member really changed: the phone sends back some it left as proposed."""
    return {key: value for key, value in modified.items() if proposed.get(key) != value}


def told_with_edit(result: Any, edited: Dict[str, Any]) -> Dict[str, Any]:
    """A write's result as the model reads it when the member changed its details first."""
    told = dict(result) if isinstance(result, dict) else {"result": result}
    told["member_edited"] = {"changed": edited, "note": MEMBER_EDITED}
    return told


def proposal_event(part: Dict[str, Any]) -> Dict[str, Any]:
    return {
        "type": "tool_approval_proposal",
        "tool_call_id": part["tool_call_id"],
        "tool": part["tool"],
        "action": part["action"],
        "arguments": part["arguments"],
    }


def pending_proposals(message: Optional[ChatMessage]) -> List[Dict[str, Any]]:
    """The cards an answer is still waiting on, in the order the model asked."""
    if message is None or message.role != "assistant":
        return []
    metadata = message.metadata_json or {}
    if PAUSED_TURN not in metadata:
        return []
    return [
        part
        for part in metadata.get("parts") or []
        if part.get("type") == "proposal" and part.get("status") == "pending"
    ]


# A change's time on its card: shown as a set, so a moved start never sits beside the old end.
TIME_DETAILS = ("start_time", "end_time", "is_all_day")


def event_on_card(
    action: Optional[str], arguments: Dict[str, Any], event: CalendarEvent, timezone_name: Optional[str]
) -> Dict[str, Any]:
    """
    The details of the event a remove or change card is about, in the names the phone reads (#63).

    A remove shows the event as it is, whatever the model wrote: the card must show what will go. A
    change keeps what the model is changing and adds only what it left out. Times are in the
    member's zone, or the calendar's own offset when the hub can't use theirs.

    [event] is the date of a repeating event the call names, or the series when it names none. A
    call naming a date the series doesn't have gets no date at all, rather than another one's.
    """
    if action not in ("delete", "update"):
        return {}
    actual: Dict[str, Any] = {"title": event.title}
    names_a_date = bool(arguments.get("occurrence_start")) and arguments.get("scope") != ALL
    if not (event.repeat and names_a_date and event.occurrence_start is None):
        zone = zone_of(timezone_name)
        actual["start_time"] = _on_card(event.start_time, event.is_all_day, zone)
        actual["end_time"] = _on_card(event.end_time, event.is_all_day, zone)
        actual["is_all_day"] = event.is_all_day
    if event.repeat:
        actual["repeat"] = event.repeat.to_dict()
    if action == "delete":
        return actual

    shown: Dict[str, Any] = {}
    if not arguments.get("title"):
        shown["title"] = actual["title"]
    if not any(key in arguments for key in TIME_DETAILS):
        shown.update({key: actual[key] for key in TIME_DETAILS if key in actual})
    if "repeat" in actual and not arguments.get("repeat"):
        shown["repeat"] = actual["repeat"]
    return shown


def _on_card(when: datetime, is_all_day: bool, zone: Optional[tzinfo]) -> str:
    """
    A time as the card carries it: a whole day is its date, anything else is to the minute. The phone
    sends a card's times back to the minute, and one that came back different would read as the
    member's own change.
    """
    if is_all_day:
        return when.date().isoformat()
    return (when.astimezone(zone) if zone else when).replace(second=0, microsecond=0).isoformat()


def as_proposed(arguments: Dict[str, Any], looked_up: Dict[str, Any]) -> Dict[str, Any]:
    """A card's arguments as the write runs them: what was only shown goes, unless the member changed it."""
    return {key: value for key, value in arguments.items() if key not in looked_up or looked_up[key] != value}


def find_proposal(message: Optional[ChatMessage], tool_call_id: str) -> Optional[Dict[str, Any]]:
    """The card for [tool_call_id] on a paused answer, decided or not; None when there is none."""
    if message is None or message.role != "assistant" or PAUSED_TURN not in (message.metadata_json or {}):
        return None
    return next(
        (
            part
            for part in message.metadata_json.get("parts") or []
            if part.get("type") == "proposal" and part.get("tool_call_id") == tool_call_id
        ),
        None,
    )


def is_awaiting_approval(messages: List[ChatMessage]) -> bool:
    """Whether the conversation's newest answer is paused on a card. Only the newest can be."""
    return bool(messages) and bool(pending_proposals(messages[-1]))


def refuse_while_awaiting_approval(messages: List[ChatMessage]) -> None:
    """A new message waits for the cards: nothing is sent, and nothing is declined for the member."""
    if is_awaiting_approval(messages):
        raise ApprovalPendingException("Answer the card above before sending another message.")


def card_to_decide(messages: List[ChatMessage], tool_call_id: str) -> Dict[str, Any]:
    """The waiting card [tool_call_id] names on the newest answer; refused if it is gone or answered."""
    card = find_proposal(messages[-1] if messages else None, tool_call_id)
    if card is None:
        raise EntityNotFoundException("There is no card waiting with that id in this conversation.")
    if card.get("status") != "pending":
        raise AlreadyDecidedException("That card has already been answered.")
    return card


def dump_messages(messages: List[LLMMessage]) -> List[Dict[str, Any]]:
    return [
        {
            "role": m.role,
            "content": m.content,
            "tool_calls": [{"id": c.id, "name": c.name, "arguments": c.arguments} for c in m.tool_calls]
            if m.tool_calls
            else None,
            "tool_call_id": m.tool_call_id,
            "name": m.name,
        }
        for m in messages
    ]


def load_messages(saved: List[Dict[str, Any]]) -> List[LLMMessage]:
    return [
        LLMMessage(
            role=m["role"],
            content=m.get("content") or "",
            tool_calls=[LLMToolCall(id=c["id"], name=c["name"], arguments=c.get("arguments") or {}) for c in m["tool_calls"]]
            if m.get("tool_calls")
            else None,
            tool_call_id=m.get("tool_call_id"),
            name=m.get("name"),
        )
        for m in saved
    ]


def without_proposals(metadata: Dict[str, Any]) -> Dict[str, Any]:
    """The answer as it was before it asked: its words and steps stay, its cards and pause go."""
    kept = {key: value for key, value in metadata.items() if key != PAUSED_TURN}
    kept["parts"] = [part for part in metadata.get("parts") or [] if part.get("type") != "proposal"]
    return kept


class DropPendingProposalsUseCase:
    """
    Drops the cards an agent's chats are waiting on, when the agent is suspended or put in the trash.

    Nothing a card asked for has run, so dropping it loses nothing: the answer keeps what it said and
    did before it asked. A later slice shows such a chat as archived.
    """

    def __init__(self, session_repo: ISessionRepository, uow: IUnitOfWork):
        self.session_repo = session_repo
        self.uow = uow

    async def for_agent(self, agent_id: str) -> None:
        async with self.uow:
            for session_id in await self.session_repo.list_ids_by_agent_id(agent_id):
                newest = await self.session_repo.get_messages(session_id, limit=1)
                if not newest or PAUSED_TURN not in (newest[-1].metadata_json or {}):
                    continue
                answer = newest[-1]
                answer.metadata_json = without_proposals(answer.metadata_json)
                await self.session_repo.update_message(answer)
            await self.uow.commit()
