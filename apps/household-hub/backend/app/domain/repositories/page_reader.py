from typing import Protocol

from app.domain.entities.source_passage import WebPage


class IPageReader(Protocol):
    """Fetches a web page and hands back its readable text. Raises PageReadException when it can't."""

    async def read(self, url: str, timeout: float = 10.0) -> WebPage:
        ...
