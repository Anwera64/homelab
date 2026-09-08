from dataclasses import dataclass
from typing import Any, Dict, Optional


@dataclass
class ToolDefinition:
    name: str
    description: str
    parameters_schema: Dict[str, Any]


@dataclass
class ToolExecutionResult:
    tool_name: str
    success: bool
    data: Optional[Any] = None
    error: Optional[str] = None
