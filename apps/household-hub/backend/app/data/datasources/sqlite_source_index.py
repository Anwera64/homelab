import logging
import math
import re
import sqlite3
from typing import Dict, List, Optional

from app.domain.entities.source_passage import SourcePassage
from app.domain.exceptions import EmbeddingException
from app.domain.repositories.embedder import IEmbedder


logger = logging.getLogger(__name__)

# Reciprocal-rank fusion's usual constant: how much a first place outweighs a tenth.
_RRF_K = 60
# How deep each ranking is read before the two are merged.
_CANDIDATES = 50


class SqliteSourceIndex:
    """
    One turn's reading, searchable two ways and merged.

    Keywords go through an in-memory SQLite FTS5 table ranked by BM25, which finds names, numbers
    and exact terms. Meaning goes through the embedder, which finds a passage that says "re-education
    facilities" when the question says "detention camps". Each ranks the passages on its own and
    reciprocal-rank fusion merges them, so a passage both agree on beats one only a single side likes.

    The embedder is optional and allowed to fail: without it the index still answers by keywords.
    """

    def __init__(self, embedder: Optional[IEmbedder] = None):
        self._embedder = embedder
        self._db = sqlite3.connect(":memory:")
        self._db.execute(
            "CREATE VIRTUAL TABLE passages USING fts5(id UNINDEXED, title, text, tokenize='porter unicode61')"
        )
        self._passages: Dict[str, SourcePassage] = {}
        self._vectors: Dict[str, List[float]] = {}

    async def add(self, passages: List[SourcePassage]) -> None:
        # An id already here keeps what it was first given: ids are what the model cites.
        new: List[SourcePassage] = []
        for passage in passages:
            if passage.id not in self._passages:
                self._passages[passage.id] = passage
                new.append(passage)
        if not new:
            return
        self._db.executemany(
            "INSERT INTO passages (id, title, text) VALUES (?, ?, ?)",
            [(p.id, p.title, p.text) for p in new],
        )
        vectors = await self._embed([f"{p.title}\n{p.text}" for p in new])
        if vectors is not None:
            for passage, vector in zip(new, vectors):
                self._vectors[passage.id] = vector

    async def search(self, query: str, k: int = 5) -> List[SourcePassage]:
        scores: Dict[str, float] = {}
        for ranking in (self._keyword_ranking(query), await self._meaning_ranking(query)):
            for rank, passage_id in enumerate(ranking):
                scores[passage_id] = scores.get(passage_id, 0.0) + 1.0 / (_RRF_K + rank + 1)
        best = sorted(scores, key=lambda passage_id: scores[passage_id], reverse=True)[:k]
        return [self._passages[passage_id] for passage_id in best]

    def _keyword_ranking(self, query: str) -> List[str]:
        # The question is the model's own words, not FTS syntax: each word is quoted, so AND, NEAR,
        # quotes and brackets are read as text and can't break the query.
        words = re.findall(r"\w+", query)
        if not words:
            return []
        match = " OR ".join('"' + word.replace('"', "") + '"' for word in words)
        rows = self._db.execute(
            "SELECT id FROM passages WHERE passages MATCH ? ORDER BY bm25(passages) LIMIT ?",
            (match, _CANDIDATES),
        ).fetchall()
        return [row[0] for row in rows]

    async def _meaning_ranking(self, query: str) -> List[str]:
        if not self._vectors:
            return []
        vectors = await self._embed([query])
        if not vectors:
            return []
        question = vectors[0]
        similarity = {
            passage_id: _cosine(question, vector) for passage_id, vector in self._vectors.items()
        }
        # Only passages that point the same way as the question count as found by meaning.
        ranked = sorted((pid for pid, s in similarity.items() if s > 0), key=similarity.get, reverse=True)
        return ranked[:_CANDIDATES]

    async def _embed(self, texts: List[str]) -> Optional[List[List[float]]]:
        if self._embedder is None:
            return None
        try:
            return await self._embedder.embed(texts)
        except EmbeddingException as exc:
            logger.warning("Source index is searching by keywords only: %s", exc)
            return None


class SqliteSourceIndexFactory:
    """A fresh index per turn: what one turn read is never seen by another."""

    def __init__(self, embedder: Optional[IEmbedder] = None):
        self._embedder = embedder

    def new(self) -> SqliteSourceIndex:
        return SqliteSourceIndex(embedder=self._embedder)


def _cosine(a: List[float], b: List[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b))
    norm = math.sqrt(sum(x * x for x in a)) * math.sqrt(sum(y * y for y in b))
    return dot / norm if norm else 0.0
