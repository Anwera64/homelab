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


@pytest.mark.asyncio
async def test_a_thinking_model_s_reasoning_is_kept_rather_than_thrown_away():
    """
    Qwen3 puts its thinking in `reasoning`, not `content` — measured against the real Ollama, 370
    of a tool call's 372 chunks were reasoning and none was content. Reading only `content` threw
    every one of them away, so the phone saw nothing at all for as long as the model thought.
    """

    def handler(request: httpx.Request) -> httpx.Response:
        sse_lines = (
            'data: {"choices": [{"delta": {"reasoning": "Okay, the user wants"}}]}\n\n'
            'data: {"choices": [{"delta": {"reasoning": " tomorrow."}}]}\n\n'
            'data: {"choices": [{"delta": {"content": "Tomorrow is light."}}]}\n\n'
            "data: [DONE]\n\n"
        )
        return httpx.Response(200, content=sse_lines.encode("utf-8"), headers={"content-type": "text/event-stream"})

    client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    connector = OllamaLLMConnector(base_url="http://mock-ollama:11434", client=client)

    chunks = [
        chunk
        async for chunk in connector.stream_chat_completion(
            messages=[LLMMessage(role="user", content="What's on tomorrow?")], model="qwen3:14b"
        )
    ]

    assert "".join(c.delta_reasoning for c in chunks) == "Okay, the user wants tomorrow."
    assert "".join(c.delta_content for c in chunks) == "Tomorrow is light."
    await connector.close()


def test_the_wait_for_the_model_covers_a_cold_load():
    """
    Loading qwen3:14b into memory takes 52.6 s on this hub, measured. A 60 s ceiling left seven
    seconds for everything else, which is why turns timed out; 120 covers the load with room.
    """
    from app.core.config import Settings

    assert Settings.model_fields["OLLAMA_TIMEOUT_SECONDS"].default == 120.0
