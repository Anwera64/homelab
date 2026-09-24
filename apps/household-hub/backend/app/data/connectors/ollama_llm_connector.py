import json
from typing import Any, AsyncGenerator, Dict, List, Optional
import httpx

from app.domain.entities.llm_message import (
    LLMMessage,
    LLMResponse,
    LLMResponseChunk,
    LLMToolCall,
)
from app.domain.repositories.llm_client import ILLMClient
from app.domain.exceptions import LLMInferenceException


class OllamaLLMConnector(ILLMClient):
    def __init__(
        self,
        base_url: str = "http://ollama:11434",
        timeout_seconds: float = 60.0,
        client: Optional[httpx.AsyncClient] = None,
    ):
        self.base_url = base_url.rstrip("/")
        self.timeout_seconds = timeout_seconds
        self._client = client or httpx.AsyncClient(timeout=timeout_seconds)
        self._owns_client = client is None

    async def close(self) -> None:
        if self._owns_client and self._client:
            await self._client.aclose()

    def _format_messages(self, messages: List[LLMMessage]) -> List[Dict[str, Any]]:
        formatted = []
        for m in messages:
            msg_dict: Dict[str, Any] = {"role": m.role, "content": m.content}
            if m.tool_calls:
                msg_dict["tool_calls"] = [
                    {
                        "id": tc.id,
                        "type": "function",
                        "function": {
                            "name": tc.name,
                            "arguments": json.dumps(tc.arguments) if isinstance(tc.arguments, dict) else str(tc.arguments),
                        },
                    }
                    for tc in m.tool_calls
                ]
            if m.tool_call_id:
                msg_dict["tool_call_id"] = m.tool_call_id
            if m.name:
                msg_dict["name"] = m.name
            formatted.append(msg_dict)
        return formatted

    def _parse_tool_calls(self, raw_tool_calls: Optional[List[Dict[str, Any]]]) -> List[LLMToolCall]:
        if not raw_tool_calls:
            return []
        parsed = []
        for tc in raw_tool_calls:
            func = tc.get("function", {})
            raw_args = func.get("arguments", "{}")
            if isinstance(raw_args, str):
                try:
                    args = json.loads(raw_args)
                except Exception:
                    args = {"raw": raw_args}
            else:
                args = raw_args or {}
            parsed.append(
                LLMToolCall(
                    id=tc.get("id", ""),
                    name=func.get("name", ""),
                    arguments=args,
                )
            )
        return parsed

    async def chat_completion(
        self,
        messages: List[LLMMessage],
        model: str,
        temperature: float = 0.7,
        top_p: float = 0.9,
        tools: Optional[List[Dict[str, Any]]] = None,
    ) -> LLMResponse:
        url = f"{self.base_url}/v1/chat/completions"
        payload: Dict[str, Any] = {
            "model": model,
            "messages": self._format_messages(messages),
            "temperature": temperature,
            "top_p": top_p,
            "stream": False,
        }
        if tools:
            payload["tools"] = tools

        try:
            resp = await self._client.post(url, json=payload)
            if resp.status_code != 200:
                raise LLMInferenceException(
                    f"LLM inference returned HTTP {resp.status_code}: {resp.text}"
                )
            data = resp.json()
            choice = data.get("choices", [{}])[0]
            msg = choice.get("message", {})
            content = msg.get("content") or ""
            tool_calls = self._parse_tool_calls(msg.get("tool_calls"))
            finish_reason = choice.get("finish_reason")
            usage = data.get("usage", {})
            return LLMResponse(
                content=content,
                tool_calls=tool_calls,
                finish_reason=finish_reason,
                usage=usage,
            )
        except httpx.TimeoutException as e:
            raise LLMInferenceException(f"LLM inference timed out: {e}")
        except httpx.ConnectError as e:
            raise LLMInferenceException(
                f"Failed to connect to LLM at {self.base_url}: {e}"
            )
        except LLMInferenceException:
            raise
        except Exception as e:
            raise LLMInferenceException(f"Unexpected LLM inference error: {e}")

    async def stream_chat_completion(
        self,
        messages: List[LLMMessage],
        model: str,
        temperature: float = 0.7,
        top_p: float = 0.9,
        tools: Optional[List[Dict[str, Any]]] = None,
    ) -> AsyncGenerator[LLMResponseChunk, None]:
        url = f"{self.base_url}/v1/chat/completions"
        payload: Dict[str, Any] = {
            "model": model,
            "messages": self._format_messages(messages),
            "temperature": temperature,
            "top_p": top_p,
            "stream": True,
        }
        if tools:
            payload["tools"] = tools

        try:
            async with self._client.stream("POST", url, json=payload) as resp:
                if resp.status_code != 200:
                    error_text = await resp.aread()
                    raise LLMInferenceException(
                        f"LLM inference stream returned HTTP {resp.status_code}: {error_text.decode('utf-8', errors='replace')}"
                    )

                async for line in resp.aiter_lines():
                    line = line.strip()
                    if not line:
                        continue
                    if line.startswith("data: "):
                        data_str = line[6:].strip()
                        if data_str == "[DONE]":
                            break
                        try:
                            chunk_data = json.loads(data_str)
                            choice = chunk_data.get("choices", [{}])[0]
                            delta = choice.get("delta", {})
                            content_delta = delta.get("content") or ""
                            reasoning_delta = delta.get("reasoning") or ""
                            tool_calls = self._parse_tool_calls(delta.get("tool_calls"))
                            finish_reason = choice.get("finish_reason")
                            yield LLMResponseChunk(
                                delta_content=content_delta,
                                delta_reasoning=reasoning_delta,
                                tool_calls=tool_calls,
                                finish_reason=finish_reason,
                            )
                        except json.JSONDecodeError:
                            continue
        except httpx.TimeoutException as e:
            raise LLMInferenceException(f"LLM inference timed out: {e}")
        except httpx.ConnectError as e:
            raise LLMInferenceException(
                f"Failed to connect to LLM at {self.base_url}: {e}"
            )
        except LLMInferenceException:
            raise
        except Exception as e:
            raise LLMInferenceException(f"Unexpected LLM inference stream error: {e}")
