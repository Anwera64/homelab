import logging
from typing import List

from app.domain.entities.llm_message import LLMMessage
from app.domain.entities.session import ChatMessage
from app.domain.exceptions import DomainException
from app.domain.repositories.llm_client import ILLMClient
from app.domain.repositories.session_repository import ISessionRepository
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.use_cases.chat.token_estimate import estimate_tokens
from app.domain.use_cases.models.resolve_agent_model import ResolveAgentModelUseCase


logger = logging.getLogger(__name__)

# The last question and its answer always stay word for word: they are what the next turn most
# likely follows up on.
_ALWAYS_KEPT = 2

_INSTRUCTIONS = (
    "You keep the running summary of a conversation between a household member and their "
    "assistant. Rewrite the summary so it also covers the new messages below. Keep what a later "
    "turn could need: facts and figures, decisions, the member's preferences and plans, questions "
    "still open, and the sources an answer relied on (titles and URLs). Drop greetings, filler and "
    "anything repeated. Write plain prose in the third person, at most {words} words. Reply with "
    "the summary only."
)


class SummarizeHistoryUseCase:
    """
    Folds a chat's older messages into its running summary, so the prompt carries what was said
    instead of the first 100 characters of it.

    It runs after a turn, in the background, and does nothing until the messages the summary
    doesn't cover outgrow [history_tokens]. Then it folds in the oldest of them until the rest fit
    in half of that, which leaves room for a few more turns before it has to run again - one model
    call every few turns rather than every turn, on a GPU that is also answering.

    If the model fails or answers with nothing, the old summary stands and the next turn tries again.
    """

    def __init__(
        self,
        session_repo: ISessionRepository,
        llm_client: ILLMClient,
        model_resolver: ResolveAgentModelUseCase,
        uow: IUnitOfWork,
        history_tokens: int = 6000,
        summary_tokens: int = 1000,
    ):
        self.session_repo = session_repo
        self.llm_client = llm_client
        self.model_resolver = model_resolver
        self.uow = uow
        self.history_tokens = history_tokens
        self.summary_tokens = summary_tokens

    async def execute(self, session_id: str) -> bool:
        """Whether a new summary was saved."""
        session = await self.session_repo.get_by_id(session_id)
        if session is None:
            return False

        pending = await self.session_repo.get_messages_after(session.id, session.summarized_through_id)
        if _tokens(pending) <= self.history_tokens:
            return False

        folded: List[ChatMessage] = []
        kept = list(pending)
        while len(kept) > _ALWAYS_KEPT and _tokens(kept) > self.history_tokens / 2:
            folded.append(kept.pop(0))
        if not folded:
            return False

        try:
            response = await self.llm_client.chat_completion(
                messages=self._prompt(session.history_summary, folded),
                model=await self.model_resolver.default(),
                temperature=0.1,
                tools=None,
            )
        except DomainException as exc:
            logger.warning("Could not summarise session %s: %s", session.id, exc)
            return False

        summary = (response.content or "").strip()
        if not summary:
            logger.warning("Summary for session %s came back empty; keeping the old one", session.id)
            return False

        async with self.uow:
            await self.session_repo.save_history_summary(session.id, summary, folded[-1].id)
            await self.uow.commit()
        return True

    def _prompt(self, previous: str | None, folded: List[ChatMessage]) -> List[LLMMessage]:
        # About 0.7 words per token.
        words = int(self.summary_tokens * 0.7)
        transcript = "\n\n".join(f"{m.role}: {m.content}" for m in folded)
        return [
            LLMMessage(role="system", content=_INSTRUCTIONS.format(words=words)),
            LLMMessage(role="user", content=f"Summary so far:\n{previous or '(none yet)'}"),
            LLMMessage(role="user", content=f"New messages:\n{transcript}"),
        ]


def _tokens(messages: List[ChatMessage]) -> float:
    return sum(estimate_tokens(m.content) for m in messages)
