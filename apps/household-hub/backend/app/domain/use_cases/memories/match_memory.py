import logging
import math
from dataclasses import dataclass
from typing import Optional, Sequence, Tuple

from app.domain.entities.memory import AgentMemory
from app.domain.entities.memory_proposal import MemoryProposal
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


@dataclass
class HeldMatch:
    """A sentence the member asked to `remember` that still waits on someone else's answer."""

    proposal: MemoryProposal
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
        if memory_id is not None:
            block = await self.memory_block.execute(user_id, is_secret=False)
            pinned = next((m for m in block.visible if m.id == memory_id), None)
            return MemoryMatch(pinned) if pinned else None
        stored, _ = await self.execute_with_held(user_id, text, ())
        return stored

    async def execute_with_held(
        self, user_id: str, text: str, held: Sequence[MemoryProposal]
    ) -> Tuple[Optional[MemoryMatch], Optional[HeldMatch]]:
        """The closest stored memory and the closest of the member's [held] sentences, which aren't stored
        yet and so have no vectors: they go to the embedder with the query, in the same call."""
        block = await self.memory_block.execute(user_id, is_secret=False)
        visible = block.visible
        quoted = _normalized(text)
        if not quoted or not (visible or held):
            return None, None
        for memory in visible:
            if quoted in (_normalized(memory.content), _normalized(block.lines.get(memory.id, ""))):
                return MemoryMatch(memory), None
        for proposal in held:
            if quoted == _normalized(proposal.content):
                return None, HeldMatch(proposal)

        try:
            query, *held_vectors = await self.embedder.embed([text] + [p.content for p in held])
        except Exception as exc:
            logger.warning("Memory match | member=%s embed failed: %s", user_id, exc)
            return None, None
        stored: Optional[MemoryMatch] = None
        if visible:
            scores = await self.vector_repo.best_scores(query, [m.id for m in visible], self.model)
            close = [m for m in visible if scores.get(m.id, 0.0) >= self.bar]
            if close:
                best = max(close, key=lambda m: scores[m.id])
                stored = MemoryMatch(best, scores[best.id])
        waiting = [HeldMatch(p, _cosine(query, v)) for p, v in zip(held, held_vectors)]
        waiting = [w for w in waiting if w.score >= self.bar]
        return stored, max(waiting, key=lambda w: w.score) if waiting else None


def _cosine(a: Sequence[float], b: Sequence[float]) -> float:
    norm = math.sqrt(sum(x * x for x in a)) * math.sqrt(sum(y * y for y in b))
    return sum(x * y for x, y in zip(a, b)) / norm if norm else 0.0
