from dataclasses import dataclass, field
from typing import List


@dataclass
class SearchResultItem:
    title: str
    url: str
    snippet: str
    engine: str = "general"
    score: float = 0.0


@dataclass
class SearchResult:
    query: str
    category: str = "general"
    total_results: int = 0
    is_cached: bool = False
    results: List[SearchResultItem] = field(default_factory=list)
