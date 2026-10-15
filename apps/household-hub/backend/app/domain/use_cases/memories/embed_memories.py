import logging
import time
from typing import List

from app.domain.entities.memory import AgentMemory
from app.domain.repositories.embedder import IEmbedder
from app.domain.repositories.memory_repository import IMemoryRepository
from app.domain.repositories.memory_vector_repository import CONTENT, RELEVANT_FOR, IMemoryVectorRepository
from app.domain.repositories.unit_of_work import IUnitOfWork

logger = logging.getLogger(__name__)


def relevant_for_text(memory: AgentMemory) -> str:
    """The `relevant_for` list as one line to embed; empty when the memory has none."""
    return "; ".join(item for item in (memory.relevant_for or []) if item)


class EmbedMemoriesUseCase:
    """
    Gives memories their vectors (memory spec §4.3): one for the statement, one for its
    `relevant_for` list. If the embedder is down the memories stay as they are, without vectors,
    and the filling pass makes them later. It never raises: a memory is saved whether or not it
    could be embedded.
    """

    def __init__(
        self,
        embedder: IEmbedder,
        vector_repo: IMemoryVectorRepository,
        memory_repo: IMemoryRepository,
        uow: IUnitOfWork,
        model: str,
    ):
        self.embedder = embedder
        self.vector_repo = vector_repo
        self.memory_repo = memory_repo
        self.uow = uow
        self.model = model

    async def execute(self, memories: List[AgentMemory]) -> int:
        """Embeds and saves each memory's vectors; returns how many memories got them."""
        if not memories:
            return 0
        jobs: list[tuple[str, str, str]] = []
        for memory in memories:
            jobs.append((memory.id, CONTENT, memory.content))
            listed = relevant_for_text(memory)
            if listed:
                jobs.append((memory.id, RELEVANT_FOR, listed))

        started = time.monotonic()
        try:
            vectors = await self.embedder.embed([text for _, _, text in jobs])
        except Exception as exc:
            logger.warning(
                "Memory embed failed | memories=%d ids=%s error=%s",
                len(memories), ",".join(m.id for m in memories), exc,
            )
            return 0

        async with self.uow:
            for (memory_id, kind, _), vector in zip(jobs, vectors):
                await self.vector_repo.save(memory_id, kind, self.model, vector)
            await self.uow.commit()
        logger.info(
            "Memory embedded | memories=%d vectors=%d ids=%s ms=%d",
            len(memories), len(jobs), ",".join(m.id for m in memories), (time.monotonic() - started) * 1000,
        )
        return len(memories)

    async def fill_missing(self) -> int:
        """The filling pass: memories with no vector from the current model get one."""
        missing_ids = await self.vector_repo.missing(self.model)
        if not missing_ids:
            return 0
        memories = [m for m in [await self.memory_repo.get_by_id(i) for i in missing_ids] if m is not None]
        filled = await self.execute(memories)
        logger.info("Memory vectors filled | filled=%d missing=%d model=%s", filled, len(missing_ids), self.model)
        return filled
