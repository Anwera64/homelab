"""
Reflection runs only on a model that is already loaded, and never keeps it longer (memory spec §2.2).
`GET /api/ps` says what is loaded and until when; a call with `keep_alive` goes to the native
`/api/chat`, which honours it.
"""

import json
from datetime import datetime, timezone

import httpx
import pytest

from app.data.connectors.ollama_llm_connector import OllamaLLMConnector
from app.domain.entities.llm_message import LLMMessage

_PS = {
    "models": [
        {"name": "bge-m3:latest", "model": "bge-m3:latest", "expires_at": "2026-10-15T13:00:00Z"},
        {"name": "qwen3.8-rvn:latest", "model": "qwen3.8-rvn:latest", "expires_at": "2026-10-15T12:14:30.500000123+02:00"},
    ]
}


def _connector(handler) -> OllamaLLMConnector:
    return OllamaLLMConnector(base_url="http://desktop:11434", client=httpx.AsyncClient(transport=httpx.MockTransport(handler)))


@pytest.mark.asyncio
@pytest.mark.parametrize("name", ["qwen3.8-rvn", "qwen3.8-rvn:latest"])
async def test_GIVEN_the_model_is_loaded_WHEN_asked_THEN_its_expiry_comes_back_in_utc(name):
    def handler(request):
        assert request.method == "GET" and request.url.path == "/api/ps"
        return httpx.Response(200, json=_PS)

    until = await _connector(handler).loaded_until(name)

    assert until == datetime(2026, 10, 15, 10, 14, 30, 500000, tzinfo=timezone.utc)


@pytest.mark.asyncio
async def test_GIVEN_another_model_is_loaded_WHEN_asked_THEN_none():
    assert await _connector(lambda r: httpx.Response(200, json=_PS)).loaded_until("llama3") is None


@pytest.mark.asyncio
async def test_GIVEN_ollama_is_down_WHEN_asked_THEN_none_rather_than_an_error():
    def handler(request):
        raise httpx.ConnectError("refused")

    assert await _connector(handler).loaded_until("qwen3.8-rvn") is None
    assert await _connector(lambda r: httpx.Response(500, text="boom")).loaded_until("qwen3.8-rvn") is None


@pytest.mark.asyncio
async def test_GIVEN_keep_alive_WHEN_completing_THEN_the_native_chat_is_called_and_the_reply_mapped():
    seen = {}

    def handler(request):
        seen["path"] = request.url.path
        seen["body"] = json.loads(request.content)
        return httpx.Response(
            200,
            json={
                "message": {"role": "assistant", "content": "<think>hmm {x}</think>\n{\"facts\": []}", "thinking": "more"},
                "done_reason": "stop",
                "prompt_eval_count": 812,
                "eval_count": 640,
            },
        )

    response = await _connector(handler).chat_completion(
        [LLMMessage(role="user", content="hi")], model="qwen3.8-rvn", temperature=0.1, keep_alive=245
    )

    assert seen["path"] == "/api/chat"
    assert seen["body"]["keep_alive"] == 245
    assert seen["body"]["stream"] is False
    assert seen["body"]["model"] == "qwen3.8-rvn"
    assert seen["body"]["options"]["temperature"] == 0.1
    assert seen["body"]["messages"] == [{"role": "user", "content": "hi"}]
    assert response.content == '{"facts": []}'
    assert response.finish_reason == "stop"
    assert response.usage == {"prompt_tokens": 812, "completion_tokens": 640}


@pytest.mark.asyncio
async def test_GIVEN_no_keep_alive_WHEN_completing_THEN_the_openai_route_is_used_as_before():
    paths = []

    def handler(request):
        paths.append(request.url.path)
        assert "keep_alive" not in json.loads(request.content)
        return httpx.Response(200, json={"choices": [{"message": {"content": "ok"}}]})

    await _connector(handler).chat_completion([LLMMessage(role="user", content="hi")], model="qwen3.8-rvn")

    assert paths == ["/v1/chat/completions"]
