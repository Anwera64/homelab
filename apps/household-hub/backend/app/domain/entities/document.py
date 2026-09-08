from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import List
import uuid


def get_utc_now() -> datetime:
    return datetime.now(timezone.utc)


@dataclass
class DocumentMetadata:
    title: str = ""
    author: str = ""
    page_count: int = 0
    format: str = "pdf"
    file_size_bytes: int = 0


@dataclass
class DocumentSection:
    heading: str
    level: int
    content: str
    page_number: int
    is_citation: bool = False


@dataclass
class ParsedDocument:
    filename: str
    metadata: DocumentMetadata
    sections: List[DocumentSection] = field(default_factory=list)
    citations: List[str] = field(default_factory=list)
    plain_text: str = ""


@dataclass
class StoredDocument:
    user_id: str
    title: str
    content: str
    space_id: str | None = None
    format: str = "markdown"
    version: int = 1
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    created_at: datetime = field(default_factory=get_utc_now)
    updated_at: datetime = field(default_factory=get_utc_now)
