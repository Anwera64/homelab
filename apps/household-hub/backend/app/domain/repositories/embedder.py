from typing import List, Protocol


class IEmbedder(Protocol):
    """Turns texts into vectors whose closeness follows their meaning, in the order given."""

    async def embed(self, texts: List[str]) -> List[List[float]]:
        ...
