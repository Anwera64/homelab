"""
What a tool part says about itself on the phone (#40).

A search used to be saved as {type, tool, success}: no query, no results, no reason when it failed,
so the phone could only say "Searched the web". The summary is the display-safe slice of a tool's
result, saved with the part so it is still there when the chat is reopened. It never carries
snippets, ids or error text: those are written for the model.
"""
from app.domain.entities.tool_definition import ToolExecutionResult
from app.domain.use_cases.chat.tool_summary import TITLE_CHARACTERS, summarize_tool


def search_result(results, query="dinner Gràcia Thursday"):
    return ToolExecutionResult(tool_name="searxng_search", success=True, data={"query": query, "results": results})


def test_a_search_says_what_it_searched_for_and_what_came_back():
    result = search_result(
        [
            {"id": "s1", "title": "The best restaurants in Gràcia", "url": "https://www.timeout.com/gracia", "snippet": "…"},
            {"id": "s2", "title": "Menu and opening hours", "url": "https://lapubilla.cat/", "snippet": "…"},
        ]
    )

    summary = summarize_tool("searxng_search", {"query": "dinner Gràcia Thursday"}, result)

    assert summary == {
        "query": "dinner Gràcia Thursday",
        "count": 2,
        "sources": [
            {"title": "The best restaurants in Gràcia", "url": "https://www.timeout.com/gracia"},
            {"title": "Menu and opening hours", "url": "https://lapubilla.cat/"},
        ],
    }


def test_a_search_with_nothing_back_still_says_what_it_searched_for():
    summary = summarize_tool("searxng_search", {"query": "q"}, search_result([], query="q"))

    assert summary == {"query": "q", "count": 0, "sources": []}


def test_a_page_read_names_its_page():
    result = ToolExecutionResult(
        tool_name="read_page",
        success=True,
        data={"page": "p1", "title": "Hong Kong: press freedom index", "url": "https://rsf.org/en/hk", "passages": 4},
    )

    summary = summarize_tool("read_page", {"source": "s1", "question": "since 2020"}, result)

    assert summary == {"sources": [{"title": "Hong Kong: press freedom index", "url": "https://rsf.org/en/hk"}]}


def test_a_failed_page_read_says_why_and_which_page():
    result = ToolExecutionResult(
        tool_name="read_page",
        success=False,
        data={"url": "https://www.scmp.com/news/1"},
        error="scmp.com blocks automated reading (Cloudflare)",
        reason="blocked",
    )

    summary = summarize_tool("read_page", {"source": "s3"}, result)

    assert summary == {"reason": "blocked", "sources": [{"title": "", "url": "https://www.scmp.com/news/1"}]}


def test_a_failed_search_keeps_its_query_and_says_why():
    result = ToolExecutionResult(
        tool_name="searxng_search", success=False, error="SearXNG unavailable", reason="service_unavailable"
    )

    summary = summarize_tool("searxng_search", {"query": "dinner Gràcia Thursday"}, result)

    assert summary == {"query": "dinner Gràcia Thursday", "reason": "service_unavailable"}


def test_a_failure_with_no_known_cause_is_unknown_never_the_error_text():
    result = ToolExecutionResult(tool_name="calendar_read", success=False, error="Traceback: something internal")

    summary = summarize_tool("calendar_read", {}, result)

    assert summary == {"reason": "unknown"}


def test_an_added_event_is_named_by_its_title():
    result = ToolExecutionResult(tool_name="calendar_write", success=True, data={"event": {"id": "e1"}})

    summary = summarize_tool("calendar_write", {"action": "create", "title": "Dinner together"}, result)

    assert summary == {"title": "Dinner together"}


def test_tools_with_nothing_to_show_have_no_summary():
    looked_up = ToolExecutionResult(tool_name="lookup_sources", success=True, data={"passages": []})
    checked = ToolExecutionResult(tool_name="calendar_read", success=True, data={"events": []})

    assert summarize_tool("lookup_sources", {"question": "q"}, looked_up) is None
    assert summarize_tool("calendar_read", {}, checked) is None


def test_long_titles_are_clipped():
    long_title = "A" * (TITLE_CHARACTERS + 50)
    result = search_result([{"title": long_title, "url": "https://example.org/"}])

    summary = summarize_tool("searxng_search", {"query": "q"}, result)

    title = summary["sources"][0]["title"]
    assert len(title) == TITLE_CHARACTERS
    assert title.endswith("…")


def test_only_web_links_reach_the_phone():
    result = search_result(
        [
            {"title": "Fine", "url": "https://example.org/"},
            {"title": "Also fine", "url": "http://example.org/"},
            {"title": "Script", "url": "javascript:alert(1)"},
            {"title": "Local", "url": "file:///etc/passwd"},
            {"title": "Nothing", "url": ""},
        ]
    )

    summary = summarize_tool("searxng_search", {"query": "q"}, result)

    assert [s["title"] for s in summary["sources"]] == ["Fine", "Also fine"]
    # The line says how many results there are, right above the list: the two agree.
    assert summary["count"] == 2


def test_a_failed_read_of_a_non_web_link_names_no_page():
    result = ToolExecutionResult(
        tool_name="read_page", success=False, data={"url": "ftp://x"}, error="Unsupported URL", reason="not_a_page"
    )

    assert summarize_tool("read_page", {"source": "ftp://x"}, result) == {"reason": "not_a_page"}
