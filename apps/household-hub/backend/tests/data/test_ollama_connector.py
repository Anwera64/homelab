import json
import pytest
import httpx

from app.domain.entities.llm_message import LLMMessage, LLMToolCall
from app.domain.exceptions import LLMInferenceException
from app.data.connectors.ollama_llm_connector import OllamaLLMConnector


@pytest.mark.asyncio
async def test_ollama_connector_chat_completion_success():
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/v1/chat/completions"
        req_body = json.loads(request.content)
        assert req_body["model"] == "qwen3:14b"
        assert req_body["stream"] is False
        assert len(req_body["messages"]) == 1

        resp_body = {
            "choices": [
                {
                    "message": {
                        "role": "assistant",
                        "content": "Hello! I am your AI assistant.",
                    },
                    "finish_reason": "stop",
                }
            ],
            "usage": {"total_tokens": 25},
        }
        return httpx.Response(200, json=resp_body)

    transport = httpx.MockTransport(handler)
    client = httpx.AsyncClient(transport=transport)
    connector = OllamaLLMConnector(base_url="http://mock-ollama:11434", client=client)

    messages = [LLMMessage(role="user", content="Hi")]
    response = await connector.chat_completion(messages=messages, model="qwen3:14b")

    assert response.content == "Hello! I am your AI assistant."
    assert response.finish_reason == "stop"
    assert response.usage["total_tokens"] == 25
    await connector.close()


@pytest.mark.asyncio
async def test_ollama_connector_chat_completion_with_tool_calls():
    def handler(request: httpx.Request) -> httpx.Response:
        resp_body = {
            "choices": [
                {
                    "message": {
                        "role": "assistant",
                        "content": "",
                        "tool_calls": [
                            {
                                "id": "call_abc123",
                                "type": "function",
                                "function": {
                                    "name": "calendar_read",
                                    "arguments": '{"start_time": "2026-09-08T00:00:00Z", "end_time": "2026-09-08T23:59:59Z"}',
                                },
                            }
                        ],
                    },
                    "finish_reason": "tool_calls",
                }
            ],
            "usage": {"total_tokens": 40},
        }
        return httpx.Response(200, json=resp_body)

    transport = httpx.MockTransport(handler)
    client = httpx.AsyncClient(transport=transport)
    connector = OllamaLLMConnector(base_url="http://mock-ollama:11434", client=client)

    messages = [LLMMessage(role="user", content="What is on my calendar today?")]
    tools = [
        {
            "type": "function",
            "function": {
                "name": "calendar_read",
                "description": "Read calendar events",
                "parameters": {"type": "object", "properties": {}},
            },
        }
    ]
    response = await connector.chat_completion(messages=messages, model="qwen3:14b", tools=tools)

    assert len(response.tool_calls) == 1
    assert response.tool_calls[0].id == "call_abc123"
    assert response.tool_calls[0].name == "calendar_read"
    assert response.tool_calls[0].arguments["start_time"] == "2026-09-08T00:00:00Z"
    await connector.close()


@pytest.mark.asyncio
async def test_ollama_connector_streaming_chat_completion():
    def handler(request: httpx.Request) -> httpx.Response:
        req_body = json.loads(request.content)
        assert req_body["stream"] is True

        # SSE stream content
        sse_lines = (
            'data: {"choices": [{"delta": {"content": "Thinking"}}]}\n\n'
            'data: {"choices": [{"delta": {"content": " of a"}}]}\n\n'
            'data: {"choices": [{"delta": {"content": " plan."}}]}\n\n'
            'data: [DONE]\n\n'
        )
        return httpx.Response(200, content=sse_lines.encode("utf-8"), headers={"content-type": "text/event-stream"})

    transport = httpx.MockTransport(handler)
    client = httpx.AsyncClient(transport=transport)
    connector = OllamaLLMConnector(base_url="http://mock-ollama:11434", client=client)

    messages = [LLMMessage(role="user", content="Plan my day")]
    collected_tokens = []
    async for chunk in connector.stream_chat_completion(messages=messages, model="qwen3:14b"):
        if chunk.delta_content:
            collected_tokens.append(chunk.delta_content)

    assert "".join(collected_tokens) == "Thinking of a plan."
    await connector.close()


@pytest.mark.asyncio
async def test_ollama_connector_error_handling():
    # 1. Non-200 status error
    def error_handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(503, text="Service Unavailable: Model loading failed")

    transport = httpx.MockTransport(error_handler)
    client = httpx.AsyncClient(transport=transport)
    connector = OllamaLLMConnector(base_url="http://mock-ollama:11434", client=client)

    with pytest.raises(LLMInferenceException) as exc_info:
        await connector.chat_completion(messages=[LLMMessage(role="user", content="Hi")], model="qwen3:14b")
    assert "503" in str(exc_info.value) or "Service Unavailable" in str(exc_info.value)
    await connector.close()

    # 2. Timeout error
    def timeout_handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectTimeout("Connection timed out")

    transport_timeout = httpx.MockTransport(timeout_handler)
    client_timeout = httpx.AsyncClient(transport=transport_timeout)
    connector_timeout = OllamaLLMConnector(base_url="http://mock-ollama:11434", client=client_timeout)

    with pytest.raises(LLMInferenceException) as exc_info2:
        await connector_timeout.chat_completion(messages=[LLMMessage(role="user", content="Hi")], model="qwen3:14b")
    assert "timed out" in str(exc_info2.value).lower()
    await connector_timeout.close()
