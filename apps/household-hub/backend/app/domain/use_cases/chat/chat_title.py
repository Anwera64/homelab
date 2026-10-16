# Long enough to recognise a chat by, short enough for one line of the Chats list.
TITLE_CHARACTERS = 40
ELLIPSIS = "…"


def title_from_first_message(content: str) -> str:
    """
    A new chat's title: the start of the member's first message, on one line, cut at a word with
    "…" when long (memory spec §2.3). Reflection replaces it with a proper one later.
    """
    text = " ".join((content or "").split())
    if len(text) <= TITLE_CHARACTERS:
        return text
    cut = text[:TITLE_CHARACTERS]
    space = cut.rfind(" ")
    ends_on_a_word = text[TITLE_CHARACTERS] == " "
    if not ends_on_a_word and space > TITLE_CHARACTERS // 2:
        cut = cut[:space]
    return cut.rstrip(" ,;:.-") + ELLIPSIS
