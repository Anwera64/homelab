import logging
from dataclasses import dataclass
from typing import Optional

from app.domain.entities.memory import AgentMemory
from app.domain.repositories.embedder import IEmbedder
from app.domain.repositories.memory_vector_repository import IMemoryVectorRepository
from app.domain.use_cases.memories.memory_block import BuildMemoryBlockUseCase

logger = logging.getLogger(__name__)


def _normalized(text: str) -> str:
    return " ".join((text or "").lower().split())


@dataclass
class MemoryMatch:
    """The stored memory a call means, and how close it scored. A quoted or pinned memory has no score."""

    memory: AgentMemory
    score: Optional[float] = None


class MatchMemoryUseCase:
    """
    Finds the stored memory a `remember` or `forget` call quotes (memory spec §2.1). Agents see
    memories as sentences, never ids, so a call names one by its text. Only what the member's agents
    can see is searched: their own memories and the household's, never another member's personal ones.

    A sentence copied from the prompt matches as it is. Anything else is scored by similarity and the
    closest above the bar matches; with nothing above it, or the embedder down, nothing does.
    """

    def __init__(
        self,
        memory_block: BuildMemoryBlockUseCase,
        embedder: IEmbedder,
        vector_repo: IMemoryVectorRepository,
        model: str,
        bar: float = 0.60,
    ):
        self.memory_block = memory_block
        self.embedder = embedder
        self.vector_repo = vector_repo
        self.model = model
        self.bar = bar

    async def execute(self, user_id: str, text: str, memory_id: Optional[str] = None) -> Optional[MemoryMatch]:
        """[memory_id] is the memory a card already showed: that one, if the member can still see it."""
        block = await self.memory_block.execute(user_id, is_secret=False)
        visible = block.visible
        if memory_id is not None:
            pinned = next((m for m in visible if m.id == memory_id), None)
            return MemoryMatch(pinned) if pinned else None

        quoted = _normalized(text)
        if not quoted or not visible:
            return None
        for memory in visible:
            if quoted in (_normalized(memory.content), _normalized(block.lines.get(memory.id, ""))):
                return MemoryMatch(memory)

        try:
            (query,) = await self.embedder.embed([text])
        except Exception as exc:
            logger.warning("Memory match | member=%s embed failed: %s", user_id, exc)
            return None
        scores = await self.vector_repo.best_scores(query, [m.id for m in visible], self.model)
        close = [m for m in visible if scores.get(m.id, 0.0) >= self.bar]
        if not close:
            return None
        best = max(close, key=lambda m: scores[m.id])
        return MemoryMatch(best, scores[best.id])
