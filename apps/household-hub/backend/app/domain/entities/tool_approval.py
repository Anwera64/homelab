from dataclasses import dataclass


@dataclass(frozen=True)
class ToolApproval:
    """
    Whether agents may do one write action for a member without asking first.

    [always_asks] marks the actions that can never be auto-approved: removing an event and replacing
    a note, which can't be undone from the chat.
    """

    tool: str
    action: str
    auto: bool = False
    always_asks: bool = False
