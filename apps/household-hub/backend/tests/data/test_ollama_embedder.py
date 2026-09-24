import json
import pytest
import httpx

from app.domain.exceptions import EmbeddingException
from app.data.connectors.ollama_embedder import OllamaEmbedder


@pytest.mark.asyncio
async def test_GIVEN_texts_WHEN_embed_is_called_THEN_it_posts_model_and_input_and_returns_vectors_in_order():
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/api/embed"
        req_body = json.loads(request.content)
        assert req_body["model"] == "bge-m3-cpu"
        assert req_body["input"] == ["hello", "world"]

        resp_body = {"embeddings": [[0.1, 0.2, 0.3], [0.4, 0.5, 0.6]]}
        return httpx.Response(200, json=resp_body)

    transport = httpx.MockTransport(handler)
    client = httpx.AsyncClient(transport=transport)
    embedder = OllamaEmbedder(base_url="http://mock-ollama:11434", client=client)

    result = await embedder.embed(["hello", "world"])

    assert result == [[0.1, 0.2, 0.3], [0.4, 0.5, 0.6]]
    await embedder.close()


@pytest.mark.asyncio
async def test_GIVEN_an_empty_list_WHEN_embed_is_called_THEN_no_http_request_is_made():
    def handler(request: httpx.Request) -> httpx.Response:
        raise AssertionError("No HTTP request should be made for an empty input list")

    transport = httpx.MockTransport(handler)
    client = httpx.AsyncClient(transport=transport)
    embedder = OllamaEmbedder(base_url="http://mock-ollama:11434", client=client)

    result = await embedder.embed([])

    assert result == []
    await embedder.close()


@pytest.mark.asyncio
async def test_GIVEN_a_non_200_response_WHEN_embed_is_called_THEN_it_raises_EmbeddingException():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(503, text="Service Unavailable: Model loading failed")

    transport = httpx.MockTransport(handler)
    client = httpx.AsyncClient(transport=transport)
    embedder = OllamaEmbedder(base_url="http://mock-ollama:11434", client=client)

    with pytest.raises(EmbeddingException) as exc_info:
        await embedder.embed(["hello"])
    assert "503" in str(exc_info.value)
    assert "Service Unavailable" in str(exc_info.value)
    await embedder.close()


@pytest.mark.asyncio
async def test_GIVEN_a_timeout_WHEN_embed_is_called_THEN_it_raises_EmbeddingException():
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectTimeout("Connection timed out")

    transport = httpx.MockTransport(handler)
    client = httpx.AsyncClient(transport=transport)
    embedder = OllamaEmbedder(base_url="http://mock-ollama:11434", client=client)

    with pytest.raises(EmbeddingException) as exc_info:
        await embedder.embed(["hello"])
    assert "timed out" in str(exc_info.value).lower()
    await embedder.close()


@pytest.mark.asyncio
async def test_GIVEN_a_connect_error_WHEN_embed_is_called_THEN_it_raises_EmbeddingException_mentioning_base_url():
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("Connection refused")

    transport = httpx.MockTransport(handler)
    client = httpx.AsyncClient(transport=transport)
    embedder = OllamaEmbedder(base_url="http://mock-ollama:11434", client=client)

    with pytest.raises(EmbeddingException) as exc_info:
        await embedder.embed(["hello"])
    assert "http://mock-ollama:11434" in str(exc_info.value)
    await embedder.close()


@pytest.mark.asyncio
async def test_GIVEN_a_vector_count_mismatch_WHEN_embed_is_called_THEN_it_raises_EmbeddingException():
    def handler(request: httpx.Request) -> httpx.Response:
        resp_body = {"embeddings": [[0.1, 0.2, 0.3]]}
        return httpx.Response(200, json=resp_body)

    transport = httpx.MockTransport(handler)
    client = httpx.AsyncClient(transport=transport)
    embedder = OllamaEmbedder(base_url="http://mock-ollama:11434", client=client)

    with pytest.raises(EmbeddingException) as exc_info:
        await embedder.embed(["hello", "world"])
    assert "2" in str(exc_info.value) and "1" in str(exc_info.value)
    await embedder.close()
