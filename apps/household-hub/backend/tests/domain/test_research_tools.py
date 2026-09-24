"""
Research reads through a per-turn index (#35 follow-up).

A turn used to carry every search result in the prompt until it ended, and ten searches filled the
model's 16k window before it could answer. Results and pages now go into the turn's index; the
prompt gets short receipts and the passages the model looks up.
"""
from typing import List, Optional

import pytest

from app.domain.entities.search_result import SearchResult, SearchResultItem
from app.domain.entities.source_passage import SourcePassage, WebPage
from app.domain.exceptions import PageReadException, ToolPermissionDeniedException
from app.domain.use_cases.integrations.execute_tool import ExecuteToolUseCase, effective_tool_permissions
from app.domain.use_cases.integrations.list_available_tools import ListAvailableToolsUseCase
from app.domain.use_cases.integrations.turn_sources import TurnSources, chunk_text


class FakeSearchConnector:
    def __init__(self, count: int = 10, snippet: str = "x" * 1000):
        self.count = count
        self.snippet = snippet
        self.limits: List[int] = []

    async def search(self, query, category="general", engines=None, fresh=False, limit=10, timeout=8.0):
        self.limits.append(limit)
        items = [
            SearchResultItem(title=f"{query} {i}", url=f"https://news.example/{query}/{i}", snippet=self.snippet, engine="bing")
            for i in range(1, min(limit, self.count) + 1)
        ]
        return SearchResult(query=query, total_results=len(items), results=items)

    async def ping(self, timeout=3.0):
        return True


class FakePageReader:
    def __init__(self, text: str = "", fail: bool = False):
        self.text = text
        self.fail = fail
        self.urls: List[str] = []

    async def read(self, url: str, timeout: float = 10.0) -> WebPage:
        self.urls.append(url)
        if self.fail:
            raise PageReadException("https://blocked.example returned HTTP 403")
        return WebPage(url=url, title="The Article", text=self.text)


class ListIndex:
    """Finds whatever shares a word with the question, in the order it was added."""

    def __init__(self):
        self.passages: List[SourcePassage] = []

    async def add(self, passages: List[SourcePassage]) -> None:
        self.passages.extend(passages)

    async def search(self, query: str, k: int = 5) -> List[SourcePassage]:
        words = set(query.lower().split())
        return [p for p in self.passages if words & set(p.text.lower().split())][:k]


def _executor(search=None, page_reader=None) -> ExecuteToolUseCase:
    return ExecuteToolUseCase(
        calendar_repo=None,
        calendar_connector=None,
        search_connector=search or FakeSearchConnector(),
        document_repo=None,
        document_reader=None,
        cipher=None,
        uow=None,
        page_reader=page_reader or FakePageReader(),
    )


async def _run(executor, tool, arguments, sources: Optional[TurnSources] = None, permissions=("searxng_search",)):
    return await executor.execute(
        tool_name=tool,
        arguments=arguments,
        user_id="u1",
        agent_tool_permissions=list(permissions),
        sources=sources,
    )


# --- search -------------------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_GIVEN_no_turn_index_WHEN_searching_THEN_five_lean_results_come_back_inline():
    search = FakeSearchConnector()

    result = await _run(_executor(search), "searxng_search", {"query": "tariffs"})

    assert search.limits == [5]
    assert result.success
    assert set(result.data) == {"query", "results"}
    assert all(set(r) == {"title", "url", "snippet"} for r in result.data["results"])
    assert all(len(r["snippet"]) <= 300 for r in result.data["results"])


@pytest.mark.asyncio
async def test_GIVEN_a_turn_index_WHEN_searching_twice_THEN_results_are_indexed_and_numbered_across_the_turn():
    index = ListIndex()
    sources = TurnSources(index)
    executor = _executor()

    first = await _run(executor, "searxng_search", {"query": "tariffs"}, sources)
    second = await _run(executor, "searxng_search", {"query": "quotas"}, sources)

    assert [r["id"] for r in first.data["results"]] == ["s1", "s2", "s3", "s4", "s5"]
    assert [r["id"] for r in second.data["results"]] == ["s6", "s7", "s8", "s9", "s10"]
    assert all(len(r["snippet"]) <= 120 for r in first.data["results"])
    assert [p.id for p in index.passages] == [f"s{i}" for i in range(1, 11)]
    # The index keeps the whole snippet; only the receipt is short.
    assert len(index.passages[0].text) == 1000


# --- read_page ----------------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_GIVEN_a_url_WHEN_a_page_is_read_THEN_its_passages_are_indexed_and_a_receipt_comes_back():
    index = ListIndex()
    page_text = "\n\n".join(f"Paragraph {i} " + "word " * 60 for i in range(6))
    reader = FakePageReader(text=page_text)

    result = await _run(
        _executor(page_reader=reader), "read_page", {"source": "https://news.example/a"}, TurnSources(index)
    )

    assert result.success
    assert reader.urls == ["https://news.example/a"]
    count = result.data["passages"]
    assert count == len(index.passages) > 1
    assert [p.id for p in index.passages] == [f"p1.{i}" for i in range(1, count + 1)]
    assert result.data == {"page": "p1", "title": "The Article", "url": "https://news.example/a", "passages": count}


@pytest.mark.asyncio
async def test_GIVEN_a_search_result_id_WHEN_a_page_is_read_THEN_that_results_url_is_fetched():
    reader = FakePageReader(text="Some text about tariffs.")
    sources = TurnSources(ListIndex())
    executor = _executor(page_reader=reader)
    await _run(executor, "searxng_search", {"query": "tariffs"}, sources)

    await _run(executor, "read_page", {"source": "s2"}, sources)

    assert reader.urls == ["https://news.example/tariffs/2"]


@pytest.mark.asyncio
async def test_GIVEN_an_unknown_id_WHEN_a_page_is_read_THEN_the_model_is_told_and_nothing_is_fetched():
    reader = FakePageReader(text="text")

    result = await _run(_executor(page_reader=reader), "read_page", {"source": "s9"}, TurnSources(ListIndex()))

    assert not result.success
    assert "s9" in result.error
    assert reader.urls == []


@pytest.mark.asyncio
async def test_GIVEN_a_page_that_cannot_be_read_WHEN_read_THEN_the_model_gets_the_reason():
    result = await _run(
        _executor(page_reader=FakePageReader(fail=True)), "read_page", {"source": "https://blocked.example"},
        TurnSources(ListIndex()),
    )

    assert not result.success
    assert "403" in result.error


# --- lookup_sources -----------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_GIVEN_indexed_passages_WHEN_looked_up_THEN_the_matches_come_back_with_their_ids():
    index = ListIndex()
    await index.add([
        SourcePassage(id="p1.1", title="A", url="https://a", text="cotton supply chains"),
        SourcePassage(id="p1.2", title="A", url="https://a", text="steel tariffs"),
    ])

    result = await _run(_executor(), "lookup_sources", {"question": "cotton"}, TurnSources(index))

    assert result.success
    assert result.data == {
        "passages": [{"id": "p1.1", "title": "A", "url": "https://a", "text": "cotton supply chains"}]
    }


@pytest.mark.asyncio
async def test_GIVEN_no_turn_index_WHEN_a_research_tool_is_called_THEN_it_says_it_only_works_in_a_chat_turn():
    for tool, arguments in (("read_page", {"source": "https://a"}), ("lookup_sources", {"question": "q"})):
        result = await _run(_executor(), tool, arguments, sources=None)
        assert not result.success
        assert "chat turn" in result.error


# --- permissions and definitions ----------------------------------------------------------------

@pytest.mark.asyncio
async def test_GIVEN_an_agent_without_search_WHEN_it_reads_a_page_THEN_it_is_refused():
    with pytest.raises(ToolPermissionDeniedException):
        await _run(_executor(), "read_page", {"source": "https://a"}, TurnSources(ListIndex()), permissions=["calendar_read"])


def test_GIVEN_search_permission_WHEN_expanded_THEN_reading_and_looking_up_come_with_it():
    assert effective_tool_permissions(["searxng_search"]) == ["searxng_search", "read_page", "lookup_sources"]
    assert effective_tool_permissions(["calendar_read"]) == ["calendar_read"]


def test_GIVEN_the_tool_list_WHEN_listed_THEN_it_describes_reading_and_looking_up():
    names = {t.name for t in ListAvailableToolsUseCase().execute()}
    assert {"read_page", "lookup_sources"} <= names


# --- chunking -----------------------------------------------------------------------------------

def test_GIVEN_short_paragraphs_WHEN_chunked_THEN_they_are_packed_together_up_to_the_size():
    chunks = chunk_text("one two\n\nthree four\n\nfive", size=20)
    assert chunks == ["one two\n\nthree four", "five"]


def test_GIVEN_a_paragraph_longer_than_the_size_WHEN_chunked_THEN_it_is_split_between_words():
    text = " ".join(["word"] * 100)
    chunks = chunk_text(text, size=50)
    assert all(len(c) <= 50 for c in chunks)
    assert " ".join(chunks).split() == text.split()


def test_GIVEN_blank_text_WHEN_chunked_THEN_there_are_no_chunks():
    assert chunk_text("  \n\n  ", size=50) == []


# --- read_page with a question ------------------------------------------------------------------

@pytest.mark.asyncio
async def test_GIVEN_a_question_WHEN_a_page_is_read_THEN_its_best_passages_come_back_with_the_receipt():
    """
    The first real research turn read two pages into the index and never looked anything up, so
    nothing it read reached the answer. Reading with the question hands the passages over at once.
    """
    index = ListIndex()
    sources = TurnSources(index)
    # Another page already read mentions cotton too; only this page's passages may come back.
    await index.add([SourcePassage(id="p9.1", title="Old", url="https://old", text="cotton elsewhere")])
    page_text = "\n\n".join(
        [f"cotton paragraph {i} " + "word " * 150 for i in range(5)] + ["steel " + "word " * 150]
    )

    result = await _run(
        _executor(page_reader=FakePageReader(text=page_text)),
        "read_page",
        {"source": "https://news.example/a", "question": "cotton"},
        sources,
    )

    passages = result.data["relevant"]
    assert 1 <= len(passages) <= 3
    assert all(p["id"].startswith("p1.") for p in passages)
    assert all("cotton" in p["text"] for p in passages)
    indexed = {p.id: p.text for p in index.passages}
    assert all(p["text"] == indexed[p["id"]] for p in passages)


@pytest.mark.asyncio
async def test_GIVEN_no_question_WHEN_a_page_is_read_THEN_only_the_receipt_comes_back():
    result = await _run(
        _executor(page_reader=FakePageReader(text="cotton text")),
        "read_page",
        {"source": "https://news.example/a"},
        TurnSources(ListIndex()),
    )

    assert "relevant" not in result.data


# --- why a step failed, for the phone (#40) -----------------------------------------------------

class BlockedPageReader(FakePageReader):
    async def read(self, url: str, timeout: float = 10.0) -> WebPage:
        self.urls.append(url)
        raise PageReadException(f"{url} is behind bot protection", reason="blocked")


class DownSearchConnector(FakeSearchConnector):
    async def search(self, query, category="general", engines=None, fresh=False, limit=10, timeout=8.0):
        from app.domain.exceptions import SearchServiceException

        raise SearchServiceException("SearXNG unavailable", reason="service_unavailable")


@pytest.mark.asyncio
async def test_GIVEN_a_blocked_page_WHEN_read_THEN_the_result_carries_the_reason_and_the_page_it_was():
    sources = TurnSources(ListIndex())
    executor = _executor(page_reader=BlockedPageReader())
    await _run(executor, "searxng_search", {"query": "tariffs"}, sources)

    result = await _run(executor, "read_page", {"source": "s2"}, sources)

    assert not result.success
    assert result.reason == "blocked"
    # The id is resolved here and nowhere else, so the phone learns which page from the result.
    assert result.data == {"url": "https://news.example/tariffs/2"}


@pytest.mark.asyncio
async def test_GIVEN_the_search_service_is_down_WHEN_searching_THEN_the_result_carries_the_reason():
    result = await _run(_executor(search=DownSearchConnector()), "searxng_search", {"query": "q"})

    assert not result.success
    assert result.reason == "service_unavailable"


@pytest.mark.asyncio
async def test_GIVEN_an_unknown_id_WHEN_a_page_is_read_THEN_the_reason_is_not_found():
    result = await _run(_executor(), "read_page", {"source": "s9"}, TurnSources(ListIndex()))

    assert result.reason == "not_found"
