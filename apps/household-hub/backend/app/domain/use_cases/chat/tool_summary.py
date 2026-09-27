from typing import Any, Dict, List, Optional

from app.domain.entities.tool_definition import ToolExecutionResult


# Long enough for any real page title, short enough that a scraped one can't flood the part.
TITLE_CHARACTERS = 120

# Only these open on the phone. Search results are the web's own data, so anything else is dropped.
WEB_SCHEMES = ("https://", "http://")

# What each write tool can do, so the phone can say what it did: a removal never reads as "Added".
# Both tools default to create when the model leaves the action out (list_available_tools).
WRITE_ACTIONS = {
    "calendar_write": ("create", "update", "delete"),
    "document_writer": ("create", "append", "replace"),
}


def summarize_tool(tool: str, arguments: Dict[str, Any], result: ToolExecutionResult) -> Optional[Dict[str, Any]]:
    """
    What a tool part shows on the phone: a search's query and results, the page a read was of, the
    event a write added, and why a step failed (#40).

    Saved with the part, so it is still there when the chat is reopened. It never carries snippets,
    ids or error text: those are written for the model. None when a tool has nothing to show.
    """
    arguments = arguments or {}
    data = result.data if isinstance(result.data, dict) else {}

    if not result.success:
        summary: Dict[str, Any] = _action(tool, arguments)
        if tool == "searxng_search":
            summary["query"] = str(arguments.get("query", ""))
        summary["reason"] = result.reason or "unknown"
        if tool == "read_page":
            sources = _sources([{"title": "", "url": data.get("url")}])
            if sources:
                summary["sources"] = sources
        return summary

    if tool == "searxng_search":
        sources = _sources(data.get("results") or [])
        return {"query": str(data.get("query", arguments.get("query", ""))), "count": len(sources), "sources": sources}

    if tool == "read_page":
        return {"sources": _sources([data])}

    if tool in WRITE_ACTIONS:
        summary = _action(tool, arguments)
        title = arguments.get("title")
        if title and tool == "calendar_write":
            summary["title"] = _clip(str(title))
        return summary or None

    return None


def _action(tool: str, arguments: Dict[str, Any]) -> Dict[str, Any]:
    """A write tool's action as the tool names it; one it doesn't have is left out, never guessed."""
    actions = WRITE_ACTIONS.get(tool)
    if actions is None:
        return {}
    action = arguments.get("action") or "create"
    return {"action": action} if action in actions else {}


def _sources(items: List[Any]) -> List[Dict[str, str]]:
    sources = []
    for item in items:
        if not isinstance(item, dict):
            continue
        url = str(item.get("url") or "")
        if url.lower().startswith(WEB_SCHEMES):
            sources.append({"title": _clip(str(item.get("title") or "")), "url": url})
    return sources


def _clip(text: str) -> str:
    text = " ".join(text.split())
    return text if len(text) <= TITLE_CHARACTERS else text[: TITLE_CHARACTERS - 1] + "…"
