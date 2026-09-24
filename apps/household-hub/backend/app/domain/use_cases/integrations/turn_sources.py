import re
from typing import Dict, List, Optional

from app.domain.entities.search_result import SearchResultItem
from app.domain.entities.source_passage import SourcePassage, WebPage
from app.domain.repositories.source_index import ISourceIndex


# About 200 tokens: small enough that a lookup of five fits comfortably, big enough to keep a thought whole.
PASSAGE_CHARACTERS = 800


class TurnSources:
    """
    What one turn has read, and the names the model knows it by.

    Search results are s1, s2, … and the passages of the n-th page read are pn.1, pn.2, …, numbered
    across the whole turn so an id never means two things. The passages themselves live in the
    index; this keeps the numbering and which URL each search result pointed at.
    """

    def __init__(self, index: ISourceIndex):
        self._index = index
        self._results = 0
        self._pages = 0
        self._urls: Dict[str, str] = {}

    async def add_search_results(self, items: List[SearchResultItem]) -> List[SourcePassage]:
        passages = []
        for item in items:
            self._results += 1
            passage_id = f"s{self._results}"
            self._urls[passage_id] = item.url
            passages.append(SourcePassage(id=passage_id, title=item.title, url=item.url, text=item.snippet))
        await self._index.add(passages)
        return passages

    async def add_page(self, page: WebPage) -> List[SourcePassage]:
        self._pages += 1
        passages = [
            SourcePassage(id=f"p{self._pages}.{n}", title=page.title, url=page.url, text=chunk)
            for n, chunk in enumerate(chunk_text(page.text), start=1)
        ]
        await self._index.add(passages)
        return passages

    @property
    def last_page_id(self) -> str:
        return f"p{self._pages}"

    def url_for(self, source: str) -> Optional[str]:
        """A URL as given, or the URL behind a search result's id; None for an id this turn never gave out."""
        if re.match(r"(?i)^https?://", source):
            return source
        return self._urls.get(source)

    async def lookup(self, question: str, k: int = 5) -> List[SourcePassage]:
        return await self._index.search(question, k=k)


def chunk_text(text: str, size: int = PASSAGE_CHARACTERS) -> List[str]:
    """
    Paragraphs packed together up to [size] characters, and a paragraph longer than that split
    between words. A passage never ends mid-word, so what the model reads is always whole text.
    """
    chunks: List[str] = []
    current = ""
    for paragraph in (p.strip() for p in re.split(r"\n\s*\n", text)):
        if not paragraph:
            continue
        for piece in _split_between_words(paragraph, size):
            if current and len(current) + 2 + len(piece) <= size:
                current += "\n\n" + piece
            else:
                if current:
                    chunks.append(current)
                current = piece
    if current:
        chunks.append(current)
    return chunks


def _split_between_words(paragraph: str, size: int) -> List[str]:
    if len(paragraph) <= size:
        return [paragraph]
    pieces: List[str] = []
    current = ""
    for word in paragraph.split():
        if current and len(current) + 1 + len(word) > size:
            pieces.append(current)
            current = word
        else:
            current = f"{current} {word}" if current else word
    if current:
        pieces.append(current)
    return pieces
