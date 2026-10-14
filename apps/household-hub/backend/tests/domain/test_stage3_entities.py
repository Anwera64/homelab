import pytest
from datetime import datetime, timezone, timedelta

from app.domain.entities.llm_message import (
    LLMToolCall,
    LLMMessage,
    LLMResponse,
    LLMResponseChunk,
)
from app.domain.repositories.llm_client import ILLMClient


def test_llm_tool_call_creation():
    tc = LLMToolCall(id="call_123", name="calendar_read", arguments={"start_time": "2026-09-08T00:00:00Z"})
    assert tc.id == "call_123"
    assert tc.name == "calendar_read"
    assert tc.arguments == {"start_time": "2026-09-08T00:00:00Z"}


def test_llm_message_creation():
    # User message
    msg_user = LLMMessage(role="user", content="Hello world")
    assert msg_user.role == "user"
    assert msg_user.content == "Hello world"
    assert msg_user.tool_calls is None

    # Assistant message with tool calls
    tc = LLMToolCall(id="call_1", name="searxng_search", arguments={"query": "test"})
    msg_assistant = LLMMessage(role="assistant", content="", tool_calls=[tc])
    assert msg_assistant.role == "assistant"
    assert len(msg_assistant.tool_calls) == 1
    assert msg_assistant.tool_calls[0].name == "searxng_search"

    # Tool execution message
    msg_tool = LLMMessage(role="tool", content='{"results": []}', tool_call_id="call_1", name="searxng_search")
    assert msg_tool.role == "tool"
    assert msg_tool.tool_call_id == "call_1"
    assert msg_tool.name == "searxng_search"


def test_llm_response_and_chunk():
    tc = LLMToolCall(id="call_1", name="calendar_read", arguments={})
    resp = LLMResponse(content="I checked your calendar.", tool_calls=[tc], finish_reason="stop", usage={"total_tokens": 42})
    assert resp.content == "I checked your calendar."
    assert len(resp.tool_calls) == 1
    assert resp.finish_reason == "stop"
    assert resp.usage["total_tokens"] == 42

    chunk = LLMResponseChunk(delta_content="Hello ", tool_calls=[], finish_reason=None)
    assert chunk.delta_content == "Hello "
    assert chunk.finish_reason is None


def test_protocols_runtime_checkable():
    class DummyLLM:
        async def chat_completion(self, messages, model, temperature=0.7, top_p=0.9, tools=None):
            return LLMResponse(content="dummy")

        async def stream_chat_completion(self, messages, model, temperature=0.7, top_p=0.9, tools=None):
            yield LLMResponseChunk(delta_content="dummy")

    assert isinstance(DummyLLM(), ILLMClient)
