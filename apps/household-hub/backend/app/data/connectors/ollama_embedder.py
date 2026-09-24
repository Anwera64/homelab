from typing import List, Optional
import httpx

from app.domain.repositories.embedder import IEmbedder
from app.domain.exceptions import EmbeddingException


class OllamaEmbedder(IEmbedder):
    def __init__(
        self,
        base_url: str = "http://ollama:11434",
        model: str = "bge-m3-cpu",
        timeout_seconds: float = 60.0,
        client: Optional[httpx.AsyncClient] = None,
    ):
        self.base_url = base_url.rstrip("/")
        self.model = model
        self.timeout_seconds = timeout_seconds
        self._client = client or httpx.AsyncClient(timeout=timeout_seconds)
        self._owns_client = client is None

    async def close(self) -> None:
        if self._owns_client and self._client:
            await self._client.aclose()

    async def embed(self, texts: List[str]) -> List[List[float]]:
        if not texts:
            return []

        url = f"{self.base_url}/api/embed"
        payload = {"model": self.model, "input": texts}

        try:
            resp = await self._client.post(url, json=payload)
            if resp.status_code != 200:
                raise EmbeddingException(
                    f"Embedding request returned HTTP {resp.status_code}: {resp.text[:200]}"
                )
            data = resp.json()
            embeddings = data.get("embeddings", [])
            if len(embeddings) != len(texts):
                raise EmbeddingException(
                    f"Embedding response returned {len(embeddings)} vectors for {len(texts)} inputs"
                )
            return embeddings
        except httpx.TimeoutException as e:
            raise EmbeddingException(f"Embedding request timed out: {e}")
        except httpx.ConnectError as e:
            raise EmbeddingException(
                f"Failed to connect to embedding service at {self.base_url}: {e}"
            )
        except EmbeddingException:
            raise
        except Exception as e:
            raise EmbeddingException(f"Unexpected embedding error: {e}")
