from collections import OrderedDict
import time
from typing import Dict, List, Optional, Tuple
import httpx
from app.domain.entities.search_result import SearchResult, SearchResultItem
from app.domain.exceptions import SearchServiceException
from app.domain.repositories.search_connector import ISearchConnector


class SearXNGSearchConnector(ISearchConnector):
    def __init__(
        self,
        base_url: str = "http://searxng:8080",
        cache_ttl_seconds: int = 900,
        max_cache_entries: int = 500,
        client: Optional[httpx.AsyncClient] = None,
    ):
        self.base_url = base_url.rstrip("/")
        self.cache_ttl_seconds = cache_ttl_seconds
        self.max_cache_entries = max_cache_entries
        self._external_client = client
        self._internal_client: Optional[httpx.AsyncClient] = None
        self._cache: OrderedDict[Tuple[str, str, Optional[Tuple[str, ...]]], Tuple[float, SearchResult]] = OrderedDict()

    @property
    def _client(self) -> Optional[httpx.AsyncClient]:
        """Backward compatibility for inspecting or mocking _client."""
        return self._external_client or self._internal_client

    def clear_expired_cache(self) -> int:
        now = time.time()
        expired_keys = [
            k for k, (cached_time, _) in self._cache.items()
            if now - cached_time >= self.cache_ttl_seconds
        ]
        for k in expired_keys:
            del self._cache[k]
        return len(expired_keys)

    async def _get_client(self) -> httpx.AsyncClient:
        if self._external_client:
            return self._external_client
        if self._internal_client is None or self._internal_client.is_closed:
            self._internal_client = httpx.AsyncClient(timeout=8.0)
        return self._internal_client

    async def close(self) -> None:
        """Gracefully close the internal HTTP client connection pool if owned."""
        if self._internal_client is not None and not self._internal_client.is_closed:
            await self._internal_client.aclose()
        self._internal_client = None

    async def search(
        self,
        query: str,
        category: str = "general",
        engines: Optional[List[str]] = None,
        fresh: bool = False,
        limit: int = 10,
        timeout: float = 8.0,
    ) -> SearchResult:
        cache_key = (query.strip().lower(), category, tuple(sorted(engines)) if engines else None)

        # 1. Check cache if fresh=False
        now = time.time()
        if not fresh and cache_key in self._cache:
            cached_time, cached_res = self._cache[cache_key]
            if now - cached_time < self.cache_ttl_seconds:
                self._cache.move_to_end(cache_key)
                return SearchResult(
                    query=cached_res.query,
                    category=cached_res.category,
                    total_results=cached_res.total_results,
                    is_cached=True,
                    results=cached_res.results,
                )
            else:
                del self._cache[cache_key]

        # 2. Query SearXNG JSON API
        params: Dict[str, str] = {
            "q": query,
            "format": "json",
            "categories": category,
        }
        if engines:
            params["engines"] = ",".join(engines)

        client = await self._get_client()
        try:
            response = await client.get(f"{self.base_url}/search", params=params, timeout=timeout)

            if response.status_code != 200:
                raise SearchServiceException(
                    f"SearXNG returned HTTP {response.status_code}: {response.text[:200]}"
                )

            data = response.json()
            raw_results = data.get("results", [])[:limit]
            results: List[SearchResultItem] = []
            for r in raw_results:
                results.append(
                    SearchResultItem(
                        title=r.get("title", "Untitled"),
                        url=r.get("url", ""),
                        snippet=r.get("content", ""),
                        engine=r.get("engine", category),
                        score=float(r.get("score", 0.0) or 0.0),
                    )
                )

            result_entity = SearchResult(
                query=query,
                category=category,
                total_results=len(results),
                is_cached=False,
                results=results,
            )

            # Store in cache with LRU eviction
            if cache_key in self._cache:
                self._cache.move_to_end(cache_key)
            else:
                while len(self._cache) >= self.max_cache_entries:
                    self._cache.popitem(last=False)
            self._cache[cache_key] = (now, result_entity)
            return result_entity

        except (httpx.HTTPError, httpx.TimeoutException) as e:
            raise SearchServiceException(f"SearXNG search service unavailable: {str(e)}")
        except Exception as e:
            if isinstance(e, SearchServiceException):
                raise
            raise SearchServiceException(f"Unexpected error while querying SearXNG: {str(e)}")

    async def ping(self, timeout: float = 3.0) -> bool:
        client = await self._get_client()
        try:
            resp = await client.get(f"{self.base_url}/healthz", timeout=timeout)
            return resp.status_code == 200
        except Exception:
            return False
