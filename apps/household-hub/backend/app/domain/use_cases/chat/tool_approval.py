from typing import Any, Dict, List, Optional

from app.domain.entities.llm_message import LLMMessage, LLMToolCall
from app.domain.entities.session import ChatMessage
from app.domain.exceptions import AlreadyDecidedException, ApprovalPendingException, EntityNotFoundException
from app.domain.repositories.session_repository import ISessionRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.use_cases.chat.tool_summary import WRITE_ACTIONS, write_action


# What a paused answer keeps beside its parts so the turn can carry on after the member decides:
# the model's side of the turn so far, which round it paused in, and what it was asked. It lives on
# the answer's own metadata, so it is saved and deleted with the chat and follows the secret-chat
# rules without a table of its own. The phone is never sent it.
PAUSED_TURN = "paused_turn"

# What the model reads for a write the member said no to.
DECLINED = "The member declined this action. It was not done; don't try it again unless they ask."


def needs_asking(tool: str, auto_approve_writes: bool, is_turn_secret: bool) -> bool:
    """
    A write asks the member first. A secret turn never asks: writes are refused in secret mode, so a
    card would only ask for something that cannot happen.
    """
    return tool in WRITE_ACTIONS and not auto_approve_writes and not is_turn_secret


def proposal_part(call: LLMToolCall) -> Dict[str, Any]:
    return {
        "type": "proposal",
        "tool_call_id": call.id,
        "tool": call.name,
        "action": write_action(call.name, call.arguments),
        "arguments": call.arguments,
        "status": "pending",
    }


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
