from typing import List, Optional
from app.domain.entities.search_result import SearchResult
from app.domain.repositories.search_connector import ISearchConnector


REPUTABLE_ACADEMIC_ENGINES = ["arxiv", "wikipedia", "wikidata", "wolframalpha"]

GENERAL = "general"
SCIENCE = "science"


class ExecuteSearchUseCase:
    def __init__(self, search_connector: ISearchConnector):
        self.search_connector = search_connector

    async def execute(
        self,
        query: str,
        category: str = GENERAL,
        fresh: bool = False,
        limit: int = 10,
        timeout: float = 8.0,
    ) -> SearchResult:
        if category == SCIENCE:
            # Papers, data and definitions: only reputable scholarly engines
            engines = REPUTABLE_ACADEMIC_ENGINES
        else:
            # Anything else, a model's typo included, is still worth a search
            category = GENERAL
            engines = None

        return await self.search_connector.search(
            query=query,
            category=category,
            engines=engines,
            fresh=fresh,
            limit=limit,
            timeout=timeout,
        )
