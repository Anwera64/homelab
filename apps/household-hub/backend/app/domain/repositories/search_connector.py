from typing import List, Optional, Protocol
from app.domain.entities.search_result import SearchResult


class ISearchConnector(Protocol):
    async def search(
        self,
        query: str,
        category: str = "general",
        engines: Optional[List[str]] = None,
        fresh: bool = False,
        limit: int = 10,
        timeout: float = 8.0,
    ) -> SearchResult:
        ...

    async def ping(self, timeout: float = 3.0) -> bool:
        ...
