from app.domain.entities.document import ParsedDocument
from app.domain.exceptions import DocumentParsingException
from app.domain.repositories.document_reader import IDocumentReader


class ParsePdfDocumentUseCase:
    def __init__(self, document_reader: IDocumentReader, max_size_bytes: int = 25 * 1024 * 1024):
        self.document_reader = document_reader
        self.max_size_bytes = max_size_bytes

    async def execute(
        self,
        file_bytes: bytes,
        filename: str = "document.pdf",
        max_pages: int = 150,
        timeout: float = 30.0,
    ) -> ParsedDocument:
        if len(file_bytes) > self.max_size_bytes:
            mb_limit = self.max_size_bytes / (1024 * 1024)
            raise DocumentParsingException(
                f"PDF file size ({len(file_bytes)} bytes) exceeds the maximum allowed limit of {mb_limit:.1f} MB."
            )

        if not filename.lower().endswith(".pdf"):
            raise DocumentParsingException("Only PDF documents are supported.")

        return await self.document_reader.parse_pdf(
            file_bytes=file_bytes,
            filename=filename,
            max_pages=max_pages,
            timeout=timeout,
        )
