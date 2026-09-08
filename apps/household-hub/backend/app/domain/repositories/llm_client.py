from typing import Any, AsyncGenerator, Dict, List, Optional, Protocol, runtime_checkable
from app.domain.entities.llm_message import LLMMessage, LLMResponse, LLMResponseChunk


@runtime_checkable
class ILLMClient(Protocol):
    async def chat_completion(
        self,
        messages: List[LLMMessage],
        model: str,
        temperature: float = 0.7,
        top_p: float = 0.9,
        tools: Optional[List[Dict[str, Any]]] = None,
    ) -> LLMResponse:
        ...

    async def stream_chat_completion(
        self,
        messages: List[LLMMessage],
        model: str,
        temperature: float = 0.7,
        top_p: float = 0.9,
        tools: Optional[List[Dict[str, Any]]] = None,
    ) -> AsyncGenerator[LLMResponseChunk, None]:
        ...
