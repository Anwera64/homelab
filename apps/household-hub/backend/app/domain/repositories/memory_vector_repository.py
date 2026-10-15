from typing import Dict, List, Protocol

# A memory has up to two vectors: its statement, and its `relevant_for` list (memory spec §4.1).
CONTENT = "content"
RELEVANT_FOR = "relevant_for"


class IMemoryVectorRepository(Protocol):
    """Where memory vectors are kept and searched. How they're stored and ranked is the data layer's."""

    async def save(self, memory_id: str, kind: str, model: str, vector: List[float]) -> None:
        """Stores the vector of that kind for the memory, replacing one already there."""
        ...

    async def missing(self, model: str) -> List[str]:
        """Active memories without a vector from `model` for each kind they have, oldest first."""
        ...

    async def best_scores(self, query: List[float], memory_ids: List[str], model: str) -> Dict[str, float]:
        """Cosine similarity of each memory's better vector to the query; memories without one are absent."""
        ...
