from typing import List, Protocol

from app.domain.entities.source_passage import SourcePassage


class ISourceIndex(Protocol):
    """Everything one turn has read, searchable. It lives as long as the turn and no longer."""

    async def add(self, passages: List[SourcePassage]) -> None:
        ...

    async def search(self, query: str, k: int = 5) -> List[SourcePassage]:
        ...


class ISourceIndexFactory(Protocol):
    def new(self) -> ISourceIndex:
        ...
