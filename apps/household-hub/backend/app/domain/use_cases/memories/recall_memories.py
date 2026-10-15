import asyncio
import logging
import time
from typing import Dict, List

from app.domain.entities.session import ChatMessage
from app.domain.repositories.embedder import IEmbedder
from app.domain.repositories.memory_vector_repository import IMemoryVectorRepository
from app.domain.use_cases.memories.memory_block import BuildMemoryBlockUseCase

logger = logging.getLogger(__name__)

# Where a message keeps what recall attached to it, so later turns replay the same text.
RECALLED = "recalled"
# Each side of the previous exchange is cut to this, so a long answer doesn't drown the question.
EXCHANGE_CHARACTERS = 1000
LOGGED_SCORES = 5
LOGGED_TEXT_CHARACTERS = 500


def recalled_ids(messages: List[ChatMessage]) -> set:
    return {
        item.get("id")
        for message in messages
        for item in ((message.metadata_json or {}).get(RECALLED) or [])
        if isinstance(item, dict)
    }


def _question(messages: List[ChatMessage]) -> str:
    """The member's message plus the exchange before it: "what should I get her?" means nothing alone."""
    *earlier, newest = messages
    previous_answer = next((i for i in range(len(earlier) - 1, -1, -1) if earlier[i].role == "assistant"), None)
    parts: List[str] = []
    if previous_answer is not None:
        previous_question = next(
            (earlier[i] for i in range(previous_answer - 1, -1, -1) if earlier[i].role == "user"), None
        )
        if previous_question is not None:
            parts.append((previous_question.content or "")[:EXCHANGE_CHARACTERS])
        parts.append((earlier[previous_answer].content or "")[:EXCHANGE_CHARACTERS])
    parts.append(newest.content or "")
    return "\n".join(parts)


def _swallow(task: asyncio.Task) -> None:
    if not task.cancelled():
        task.exception()


class RecallMemoriesUseCase:
    """
    Recall (memory spec §5.2), once per message the member sends. Memories the fixed block left out
    are scored against the message by the better of their two vectors; the best few above the
    cut-off are attached to the message, each with its line exactly as shown. Usually nothing passes,
    and nothing is attached.
    """

    def __init__(
        self,
        embedder: IEmbedder,
        vector_repo: IMemoryVectorRepository,
        memory_block: BuildMemoryBlockUseCase,
        model: str,
        cutoff: float = 0.60,
        max_attached: int = 3,
        wait_seconds: float = 1.0,
        log_text: bool = False,
    ):
        self.embedder = embedder
        self.vector_repo = vector_repo
        self.memory_block = memory_block
        self.model = model
        self.cutoff = cutoff
        self.max_attached = max_attached
        self.wait_seconds = wait_seconds
        self.log_text = log_text

    async def execute(
        self, user_id: str, session_id: str, messages: List[ChatMessage], is_secret: bool
    ) -> List[Dict[str, str]]:
        """`messages` is the chat's window, the member's new message last."""
        started = time.monotonic()
        block = await self.memory_block.execute(user_id, is_secret=is_secret)

        def done(reason: str, scores: Dict[str, float] | None = None, attached=()) -> List[Dict[str, str]]:
            in_block = {m.id for m in block.included}
            top = sorted((scores or {}).items(), key=lambda item: item[1], reverse=True)[:LOGGED_SCORES]
            line = (
                "Memory recall | member=%s session=%s cutoff=%.2f top=%s attached=%s reason=%s ms=%d"
                % (
                    user_id, session_id, self.cutoff,
                    ",".join(f"{i}={s:.3f}" + ("(block)" if i in in_block else "") for i, s in top),
                    ",".join(item["id"] for item in attached), reason, (time.monotonic() - started) * 1000,
                )
            )
            if self.log_text and attached:
                line += " text=" + " | ".join(item["text"][:LOGGED_TEXT_CHARACTERS] for item in attached)
            logger.info(line)
            return list(attached)

        if not block.visible:
            return done("nothing_stored")

        embedding = asyncio.ensure_future(self.embedder.embed([_question(messages)]))
        finished, _ = await asyncio.wait({embedding}, timeout=self.wait_seconds)
        if not finished:
            # Left to finish rather than cancelled: the embedder ends up loaded for the next message.
            embedding.add_done_callback(_swallow)
            return done("embedder_timed_out")
        try:
            (query,) = embedding.result()
        except Exception as exc:
            logger.warning("Memory recall | member=%s session=%s embed failed: %s", user_id, session_id, exc)
            return done("embedder_failed")

        scores = await self.vector_repo.best_scores(query, [m.id for m in block.visible], self.model)
        if not scores:
            return done("no_vectors")

        already = recalled_ids(messages[:-1])
        candidates = [
            m for m in block.left_out if m.id not in already and scores.get(m.id, 0.0) >= self.cutoff
        ]
        candidates.sort(key=lambda m: scores[m.id], reverse=True)
        attached = [{"id": m.id, "text": block.lines[m.id]} for m in candidates[: self.max_attached]]
        return done("attached" if attached else "below_cutoff", scores, attached)
