from typing import List, Optional
from app.domain.entities.search_result import SearchResult
from app.domain.repositories.search_connector import ISearchConnector


REPUTABLE_ACADEMIC_ENGINES = ["arxiv", "wikipedia", "wikidata", "wolframalpha"]


class ExecuteSearchUseCase:
    def __init__(self, search_connector: ISearchConnector):
        self.search_connector = search_connector

    async def execute(
        self,
        query: str,
        role: str = "assistant",
        fresh: bool = False,
        limit: int = 10,
        timeout: float = 8.0,
    ) -> SearchResult:
        if role == "researcher":
            # Force reputable scholarly sources and science category
            category = "science"
            engines = REPUTABLE_ACADEMIC_ENGINES
        else:
            category = "general"
            engines = None

        return await self.search_connector.search(
            query=query,
            category=category,
            engines=engines,
            fresh=fresh,
            limit=limit,
            timeout=timeout,
        )
