from dataclasses import dataclass
from typing import List, Tuple

from app.domain.entities.session import ChatMessage, ConversationSession
from app.domain.entities.user import User
from app.domain.use_cases.chat.tool_approval import card_to_decide, refuse_while_awaiting_approval
from app.domain.use_cases.sessions.get_session import GetSessionUseCase


class PrepareChatTurnUseCase:
    """
    The member's own chat and what was said in it, ready for a new message. Refused while a card
    waits: nothing is sent, and nothing is declined for the member.
    """

    def __init__(self, get_session: GetSessionUseCase):
        self.get_session = get_session

    async def execute(self, session_id: str, current_user: User) -> Tuple[ConversationSession, List[ChatMessage]]:
        session, messages = await self.get_session.execute(session_id=session_id, current_user=current_user)
        refuse_while_awaiting_approval(messages)
        return session, messages


@dataclass
class ToolDecision:
    """What a decision carries on: the question the paused answer was for, and whose answer it is."""

    question: str
    agent_id: str


class PrepareToolDecisionUseCase:
    """
    Checks a member's answer to a card before the paused turn carries on: the card must still be
    waiting on the newest answer of their own chat. 404 when it is not there, 409 when it was answered.
    """

    def __init__(self, get_session: GetSessionUseCase):
        self.get_session = get_session

    async def execute(self, session_id: str, current_user: User, tool_call_id: str) -> ToolDecision:
        session, messages = await self.get_session.execute(session_id=session_id, current_user=current_user)
        card_to_decide(messages, tool_call_id)
        question = next((m.content for m in reversed(messages) if m.role == "user"), "")
        return ToolDecision(question=question, agent_id=session.agent_id or "")
