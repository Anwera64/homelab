from dataclasses import dataclass


@dataclass(frozen=True)
class SourcePassage:
    """
    One piece of what a turn has read: a search result, or a stretch of a page.

    Research used to carry every result in the prompt for the rest of the turn, so a few searches
    filled the model's window before it could answer. Passages go into the turn's index instead,
    and the model reads the ones it asks for.
    """

    id: str
    title: str
    url: str
    text: str


@dataclass(frozen=True)
class WebPage:
    """A page as text: what a page reader hands back, before it is cut into passages."""

    url: str
    title: str
    text: str
