from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional


@dataclass
class LLMToolCall:
    id: str
    name: str
    arguments: Dict[str, Any]


@dataclass
class LLMMessage:
    role: str  # "user", "assistant", "system", "tool"
    content: str = ""
    tool_calls: Optional[List[LLMToolCall]] = None
    tool_call_id: Optional[str] = None
    name: Optional[str] = None


@dataclass
class LLMResponse:
    content: str
    tool_calls: List[LLMToolCall] = field(default_factory=list)
    finish_reason: Optional[str] = None
    usage: Dict[str, int] = field(default_factory=dict)


@dataclass
class LLMResponseChunk:
    delta_content: str = ""
    # What a thinking model says to itself before it answers. Never part of the answer.
    delta_reasoning: str = ""
    tool_calls: List[LLMToolCall] = field(default_factory=list)
    finish_reason: Optional[str] = None
