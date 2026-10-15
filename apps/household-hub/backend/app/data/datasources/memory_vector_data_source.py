import logging
import math
from array import array
from datetime import datetime, timezone
from typing import Dict, List, Protocol

from sqlalchemy import and_, bindparam, delete, func, or_, select, text
from sqlalchemy.exc import OperationalError
from sqlalchemy.ext.asyncio import AsyncSession

from app.data.models.memory_model import MemoryModel
from app.data.models.memory_vector_model import MemoryVectorModel

logger = logging.getLogger(__name__)


def pack(vector: List[float]) -> bytes:
    """32-bit floats, the layout sqlite-vec reads (memory spec §4.1)."""
    return array("f", vector).tobytes()


def unpack(blob: bytes) -> List[float]:
    values = array("f")
    values.frombytes(blob)
    return values.tolist()


def _cosine(a: List[float], b: List[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b))
    norm = math.sqrt(sum(x * x for x in a)) * math.sqrt(sum(y * y for y in b))
    return dot / norm if norm else 0.0


_SQL_SCORES = text(
    "SELECT memory_id, MAX(1 - vec_distance_cosine(vector, :query)) AS score FROM memory_vectors "
    "WHERE model = :model AND memory_id IN :ids GROUP BY memory_id"
).bindparams(bindparam("ids", expanding=True))


class IMemoryVectorDataSource(Protocol):
    async def save(self, memory_id: str, kind: str, model: str, vector: List[float]) -> None:
        ...

    async def missing(self, model: str) -> List[str]:
        ...

    async def best_scores(self, query: List[float], memory_ids: List[str], model: str) -> Dict[str, float]:
        ...


class SqliteMemoryVectorDataSource(IMemoryVectorDataSource):
    """
    Ranks with sqlite-vec's `vec_distance_cosine` on an ordinary table (§4.2). Where the extension
    isn't there, the same rows are ranked in Python: slower, same answer.
    """

    _python_fallback_logged = False

    def __init__(self, session: AsyncSession, use_extension: bool = True):
        self.session = session
        self.use_extension = use_extension

    async def save(self, memory_id: str, kind: str, model: str, vector: List[float]) -> None:
        await self.session.execute(
            delete(MemoryVectorModel).where(
                MemoryVectorModel.memory_id == memory_id, MemoryVectorModel.kind == kind
            )
        )
        self.session.add(
            MemoryVectorModel(
                memory_id=memory_id,
                kind=kind,
                model=model,
                vector=pack(vector),
                created_at=datetime.now(timezone.utc),
            )
        )
        await self.session.flush()

    async def missing(self, model: str) -> List[str]:
        def has(kind: str):
            return (
                select(MemoryVectorModel.memory_id)
                .where(
                    MemoryVectorModel.memory_id == MemoryModel.id,
                    MemoryVectorModel.kind == kind,
                    MemoryVectorModel.model == model,
                )
                .exists()
            )

        has_relevant_for = and_(
            MemoryModel.relevant_for.is_not(None), func.json_array_length(MemoryModel.relevant_for) > 0
        )
        stmt = (
            select(MemoryModel.id)
            .where(
                MemoryModel.is_active.is_(True),
                or_(~has("content"), and_(has_relevant_for, ~has("relevant_for"))),
            )
            .order_by(MemoryModel.created_at)
        )
        return list((await self.session.execute(stmt)).scalars().all())

    async def best_scores(self, query: List[float], memory_ids: List[str], model: str) -> Dict[str, float]:
        if not memory_ids:
            return {}
        if self.use_extension:
            try:
                rows = await self.session.execute(
                    _SQL_SCORES, {"query": pack(query), "model": model, "ids": list(memory_ids)}
                )
                return {memory_id: float(score) for memory_id, score in rows.all()}
            except OperationalError as exc:
                if "vec_distance_cosine" not in str(exc):
                    raise
                if not SqliteMemoryVectorDataSource._python_fallback_logged:
                    logger.warning("Memory search | sqlite-vec not on this connection, ranking in Python")
                    SqliteMemoryVectorDataSource._python_fallback_logged = True
                else:
                    logger.warning("Memory search | ranking in Python")
        return await self._python_scores(query, memory_ids, model)

    async def _python_scores(self, query: List[float], memory_ids: List[str], model: str) -> Dict[str, float]:
        rows = await self.session.execute(
            select(MemoryVectorModel.memory_id, MemoryVectorModel.vector).where(
                MemoryVectorModel.model == model, MemoryVectorModel.memory_id.in_(memory_ids)
            )
        )
        scores: Dict[str, float] = {}
        for memory_id, blob in rows.all():
            score = _cosine(query, unpack(blob))
            scores[memory_id] = max(score, scores.get(memory_id, -1.0))
        return scores
