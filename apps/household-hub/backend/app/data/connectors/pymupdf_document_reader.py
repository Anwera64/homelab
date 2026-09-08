import asyncio
import io
import re
from typing import List, Tuple
import fitz  # PyMuPDF

from app.domain.entities.document import DocumentMetadata, DocumentSection, ParsedDocument
from app.domain.exceptions import DocumentParsingException, ScannedPdfException
from app.domain.repositories.document_reader import IDocumentReader


class PyMuPDFDocumentReader(IDocumentReader):
    """
    Non-blocking PDF parser utilizing PyMuPDF (fitz) with structural sectioning,
    page tagging, citation extraction, and scanned document detection.
    """

    def _sync_parse(
        self,
        file_bytes: bytes,
        filename: str,
        max_pages: int,
    ) -> ParsedDocument:
        try:
            doc = fitz.open(stream=file_bytes, filetype="pdf")
        except Exception as e:
            raise DocumentParsingException(f"Failed to open PDF document: {str(e)}")

        total_pages = len(doc)
        pages_to_read = min(total_pages, max_pages)

        # Extract metadata
        meta = doc.metadata or {}
        title = meta.get("title") or filename
        author = meta.get("author") or "Unknown"

        full_plain_text_chunks: List[str] = []
        sections: List[DocumentSection] = []
        citations: List[str] = []
        total_text_length = 0

        citation_patterns = [
            re.compile(r"^\[\d+\]\s+.+", re.MULTILINE),
            re.compile(r"^[A-Z][a-z]+(\s+et\s+al\.)?,\s+\d{4}", re.MULTILINE),
        ]

        for pno in range(pages_to_read):
            page = doc[pno]
            page_number = pno + 1
            page_text = page.get_text("text") or ""
            stripped_text = page_text.strip()
            total_text_length += len(stripped_text)

            if stripped_text:
                full_plain_text_chunks.append(f"--- [Page {page_number}] ---\n{stripped_text}")

            # Inspect text blocks for structural headings & citations
            blocks = page.get_text("blocks") or []
            for b in blocks:
                block_text = b[4].strip() if len(b) > 4 and isinstance(b[4], str) else ""
                if not block_text:
                    continue

                # Check if block is a citation/reference entry
                is_citation = False
                lower_block = block_text.lower()
                if "references" in lower_block or "bibliography" in lower_block:
                    is_citation = True

                for cp in citation_patterns:
                    if cp.search(block_text):
                        is_citation = True
                        citations.append(block_text[:150])
                        break

                # Estimate if block is a heading (short length, no ending period, uppercase or title case)
                lines = block_text.split("\n")
                first_line = lines[0].strip()
                if len(first_line) < 80 and not first_line.endswith(".") and len(lines) <= 2:
                    heading = first_line
                    content = "\n".join(lines[1:]).strip() if len(lines) > 1 else block_text
                    level = 1 if len(heading) < 40 else 2
                else:
                    heading = f"Section (Page {page_number})"
                    content = block_text
                    level = 3

                sections.append(
                    DocumentSection(
                        heading=heading,
                        level=level,
                        content=content,
                        page_number=page_number,
                        is_citation=is_citation,
                    )
                )

        doc.close()

        # Fast Fail for Scanned PDFs with zero embedded text characters
        if total_text_length == 0:
            raise ScannedPdfException(
                "This document appears to be a scanned image with no embedded text layer. "
                "Please run OCR or upload a digital-native PDF."
            )

        metadata_entity = DocumentMetadata(
            title=title,
            author=author,
            page_count=total_pages,
            format="pdf",
            file_size_bytes=len(file_bytes),
        )

        return ParsedDocument(
            filename=filename,
            metadata=metadata_entity,
            sections=sections,
            citations=citations[:50],  # Bound citation list
            plain_text="\n\n".join(full_plain_text_chunks),
        )

    async def parse_pdf(
        self,
        file_bytes: bytes,
        filename: str = "document.pdf",
        max_pages: int = 150,
        timeout: float = 30.0,
    ) -> ParsedDocument:
        try:
            return await asyncio.wait_for(
                asyncio.to_thread(self._sync_parse, file_bytes, filename, max_pages),
                timeout=timeout,
            )
        except asyncio.TimeoutError:
            raise DocumentParsingException(
                f"PDF parsing timed out after {timeout:.1f}s. Consider parsing fewer pages or a smaller file."
            )
        except (DocumentParsingException, ScannedPdfException):
            raise
        except Exception as e:
            raise DocumentParsingException(f"Unexpected error parsing PDF: {str(e)}")
