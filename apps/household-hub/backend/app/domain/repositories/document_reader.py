from typing import Protocol
from app.domain.entities.document import ParsedDocument


class IDocumentReader(Protocol):
    async def parse_pdf(
        self,
        file_bytes: bytes,
        filename: str = "document.pdf",
        max_pages: int = 150,
        timeout: float = 30.0,
    ) -> ParsedDocument:
        ...
