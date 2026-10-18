from typing import Any, Dict, List, Optional

from app.domain.entities.tool_definition import ToolExecutionResult


# Long enough for any real page title, short enough that a scraped one can't flood the part.
TITLE_CHARACTERS = 120

# Only these open on the phone. Search results are the web's own data, so anything else is dropped.
WEB_SCHEMES = ("https://", "http://")

# What each write tool can do, so the phone can say what it did: a removal never reads as "Added".
# The calendar and notes default to create when the model leaves the action out (list_available_tools).
WRITE_ACTIONS = {
    "calendar_write": ("create", "update", "delete"),
    "document_writer": ("create", "append", "replace"),
    "remember": ("create",),
    "forget": ("delete",),
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
        return describe_write(tool, arguments) or None

    return None


def write_action(tool: str, arguments: Dict[str, Any]) -> Optional[str]:
    """A write tool's action as the tool names it; one it doesn't have is None, never guessed."""
    actions = WRITE_ACTIONS.get(tool)
    if actions is None:
        return None
    # Left out, it is the tool's first: create, or forget's only one, delete.
    action = (arguments or {}).get("action") or actions[0]
    return action if action in actions else None


def describe_write(tool: str, arguments: Dict[str, Any]) -> Dict[str, Any]:
    """What a write step shows whether it ran or not: its action, and the event it was about."""
    summary = _action(tool, arguments or {})
    title = (arguments or {}).get("title")
    if title and tool == "calendar_write":
        summary["title"] = _clip(str(title))
    elif tool in ("remember", "forget"):
        # A forget step names the memory its card showed, not the model's quote of it.
        stored = (arguments or {}).get("stored")
        stored = stored.get("content") if isinstance(stored, dict) else None
        sentence = (arguments or {}).get("content" if tool == "remember" else "memory")
        if stored or sentence:
            summary["title"] = _clip(str(stored if tool == "forget" and stored else sentence))
    return summary


def _action(tool: str, arguments: Dict[str, Any]) -> Dict[str, Any]:
    action = write_action(tool, arguments)
    return {"action": action} if action is not None else {}


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
