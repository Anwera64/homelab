import pytest
import httpx
from datetime import datetime, timezone
from unittest.mock import AsyncMock, patch, MagicMock

from app.domain.entities.integration_credential import CalendarCredential
from app.domain.exceptions import ScannedPdfException, SearchServiceException
from app.data.connectors.searxng_search_connector import SearXNGSearchConnector
from app.data.connectors.pymupdf_document_reader import PyMuPDFDocumentReader


@pytest.mark.asyncio
async def test_searxng_connector_search_and_caching():
    mock_results = {
        "query": "solar architecture",
        "number_of_results": 2,
        "results": [
            {
                "title": "Solar Facades in 2026",
                "url": "https://arxiv.org/abs/2601.0001",
                "content": "Novel photovoltaic integrations...",
                "engine": "arxiv",
                "score": 0.9,
            },
            {
                "title": "Solar Energy",
                "url": "https://en.wikipedia.org/wiki/Solar_energy",
                "content": "Solar energy is radiant light...",
                "engine": "wikipedia",
                "score": 0.8,
            },
        ],
    }

    mock_client = AsyncMock(spec=httpx.AsyncClient)
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.json.return_value = mock_results
    mock_client.get.return_value = mock_response

    connector = SearXNGSearchConnector(base_url="http://searxng:8080", cache_ttl_seconds=900, client=mock_client)

    # 1. First search -> calls client.get
    res1 = await connector.search(query="solar architecture", category="science")
    assert res1.total_results == 2
    assert res1.is_cached is False
    assert len(res1.results) == 2
    assert res1.results[0].title == "Solar Facades in 2026"
    assert mock_client.get.call_count == 1

    # 2. Repeat search with fresh=False -> served from memory cache (call_count still 1)
    res2 = await connector.search(query="solar architecture", category="science", fresh=False)
    assert res2.is_cached is True
    assert mock_client.get.call_count == 1

    # 3. Repeat search with fresh=True -> bypasses cache (call_count becomes 2)
    res3 = await connector.search(query="solar architecture", category="science", fresh=True)
    assert res3.is_cached is False
    assert mock_client.get.call_count == 2


@pytest.mark.asyncio
async def test_searxng_connector_handles_errors():
    mock_client = AsyncMock(spec=httpx.AsyncClient)
    mock_client.get.side_effect = httpx.ConnectError("Connection refused")

    connector = SearXNGSearchConnector(base_url="http://searxng:8080", client=mock_client)

    with pytest.raises(SearchServiceException):
        await connector.search(query="failing query")


@pytest.mark.asyncio
async def test_pymupdf_reader_parses_pdf_and_detects_scanned():
    import fitz  # PyMuPDF

    # Create an in-memory PDF with text, a heading, and a citation
    doc = fitz.open()
    page = doc.new_page()
    page.insert_text((50, 50), "Parametric Architectural Synthesis", fontsize=16)
    page.insert_text((50, 100), "This research explores lightweight timber structures.", fontsize=11)
    page.insert_text((50, 200), "References\n[1] Frei Otto, Tensile Structures, 1973.", fontsize=10)
    pdf_bytes = doc.tobytes()
    doc.close()

    reader = PyMuPDFDocumentReader()
    parsed = await reader.parse_pdf(file_bytes=pdf_bytes, filename="timber.pdf")

    assert parsed.filename == "timber.pdf"
    assert parsed.metadata.page_count == 1
    assert "Parametric Architectural Synthesis" in parsed.plain_text
    assert len(parsed.sections) > 0

    # Create an in-memory empty/scanned PDF with zero text characters
    scanned_doc = fitz.open()
    scanned_doc.new_page()  # Blank page, zero text
    scanned_bytes = scanned_doc.tobytes()
    scanned_doc.close()

    with pytest.raises(ScannedPdfException):
        await reader.parse_pdf(file_bytes=scanned_bytes, filename="scanned.pdf")


@pytest.mark.asyncio
async def test_caldav_update_event_atomic_in_place():
    from app.data.connectors.caldav_calendar_connector import CalDavCalendarConnector
    from app.domain.entities.integration_credential import CalendarCredential

    connector = CalDavCalendarConnector()
    cred = CalendarCredential(
        id="cred-1",
        user_id="user-1",
        provider="apple_icloud",
        url="https://caldav.example.com",
        username="user@example.com",
        encrypted_secret="enc_password",
        calendar_name="Work",
    )

    existing_ical = (
        "BEGIN:VCALENDAR\r\n"
        "VERSION:2.0\r\n"
        "PRODID:-//Example//CalDAV//EN\r\n"
        "BEGIN:VEVENT\r\n"
        "UID:event-123\r\n"
        "DTSTAMP:20260901T000000Z\r\n"
        "DTSTART:20260908T100000Z\r\n"
        "DTEND:20260908T110000Z\r\n"
        "SUMMARY:Original Title\r\n"
        "DESCRIPTION:Original Description\r\n"
        "SEQUENCE:0\r\n"
        "END:VEVENT\r\n"
        "END:VCALENDAR\r\n"
    )

    mock_event = MagicMock()
    mock_event.data = existing_ical
    mock_event.save = MagicMock()
    mock_event.delete = MagicMock()

    mock_cal = MagicMock()
    mock_cal.name = "Work"
    mock_cal.event_by_uid.return_value = mock_event

    with patch.object(connector, "_sync_get_client") as mock_get_client:
        mock_client = MagicMock()
        mock_get_client.return_value = mock_client
        with patch.object(connector, "_sync_get_target_calendar", return_value=mock_cal):
            updated = await connector.update_event(
                credential=cred,
                secret="secret123",
                event_id="event-123",
                title="Updated In-Place Title",
                description="Updated Description",
            )

            # Assert atomic in-place behavior:
            # 1. Event was retrieved by UID
            mock_cal.event_by_uid.assert_called_once_with("event-123")
            # 2. Delete was NEVER called (preserving data safety)
            mock_event.delete.assert_not_called()
            # 3. Save was called
            mock_event.save.assert_called_once()
            # 4. UID remains identical (no desync)
            assert updated.id == "event-123"
            assert updated.title == "Updated In-Place Title"
            assert updated.description == "Updated Description"


@pytest.mark.asyncio
async def test_searxng_cache_lru_eviction():
    mock_client = AsyncMock(spec=httpx.AsyncClient)
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.json.side_effect = lambda: {"query": "q", "results": [{"title": "T", "url": "U", "content": "C"}]}
    mock_client.get.return_value = mock_response

    # Initialize connector with max capacity of 2 items
    connector = SearXNGSearchConnector(base_url="http://searxng:8080", max_cache_entries=2, client=mock_client)

    # 1. Search Query 1 and Query 2 -> fills cache to capacity 2
    await connector.search(query="query 1")
    await connector.search(query="query 2")
    assert len(connector._cache) == 2

    # 2. Access Query 1 again -> makes Query 1 most recently used, Query 2 is least recently used
    res_q1 = await connector.search(query="query 1")
    assert res_q1.is_cached is True

    # 3. Search Query 3 -> triggers eviction of LRU item (Query 2)
    await connector.search(query="query 3")
    assert len(connector._cache) == 2

    # Assert Query 1 is still in cache
    res_q1_cached = await connector.search(query="query 1")
    assert res_q1_cached.is_cached is True

    # Assert Query 3 is still in cache
    res_q3_cached = await connector.search(query="query 3")
    assert res_q3_cached.is_cached is True

    # Assert Query 2 was evicted (calling it will hit client.get again)
    call_count_before = mock_client.get.call_count
    res_q2 = await connector.search(query="query 2")
    assert res_q2.is_cached is False
    assert mock_client.get.call_count == call_count_before + 1


@pytest.mark.asyncio
async def test_searxng_cache_expired_deletion():
    import time

    mock_client = AsyncMock(spec=httpx.AsyncClient)
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.json.return_value = {"results": [{"title": "T", "url": "U", "content": "C"}]}
    mock_client.get.return_value = mock_response

    # Cache with 1 second TTL
    connector = SearXNGSearchConnector(base_url="http://searxng:8080", cache_ttl_seconds=1, client=mock_client)
    await connector.search(query="expiring query")
    assert len(connector._cache) == 1

    # Simulate passage of 2 seconds
    key = list(connector._cache.keys())[0]
    ts, val = connector._cache[key]
    connector._cache[key] = (ts - 2.0, val)

    # Calling search on expired query should purge from cache and re-query
    res = await connector.search(query="expiring query")
    assert res.is_cached is False

    # Calling clear_expired_cache helper
    connector._cache[key] = (time.time() - 10.0, val)
    evicted = connector.clear_expired_cache()
    assert evicted >= 1
    assert key not in connector._cache


@pytest.mark.asyncio
async def test_searxng_connector_persistent_client_lifecycle():
    connector = SearXNGSearchConnector(base_url="http://searxng:8080")

    # Lazy initialization on first _get_client() call
    client1 = await connector._get_client()
    assert isinstance(client1, httpx.AsyncClient)
    assert not client1.is_closed

    # Second call returns the exact same client instance
    client2 = await connector._get_client()
    assert client1 is client2

    # Closing the connector closes the internal client
    await connector.close()
    assert client1.is_closed
    assert connector._internal_client is None

    # Getting client again creates a fresh unclosed client
    client3 = await connector._get_client()
    assert not client3.is_closed
    assert client3 is not client1
    await connector.close()


@pytest.mark.asyncio
async def test_searxng_connector_search_reuses_internal_client():
    connector = SearXNGSearchConnector(base_url="http://searxng:8080")

    with patch("httpx.AsyncClient.get", new_callable=AsyncMock) as mock_get:
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = {"results": []}
        mock_get.return_value = mock_response

        # Execute two searches
        await connector.search("query 1", fresh=True)
        client_after_first = connector._internal_client
        assert client_after_first is not None
        assert not client_after_first.is_closed

        await connector.search("query 2", fresh=True)
        assert connector._internal_client is client_after_first
        assert not connector._internal_client.is_closed

        await connector.close()
        assert client_after_first.is_closed




@pytest.mark.asyncio
@pytest.mark.parametrize(
    "respond",
    [
        lambda request: httpx.Response(502, text="Bad gateway"),
        lambda request: (_ for _ in ()).throw(httpx.ConnectError("refused", request=request)),
    ],
    ids=["http-error", "unreachable"],
)
async def test_searxng_failures_say_the_service_is_unavailable(respond):
    """The phone says why a search failed from this code; the message is for the model (#40)."""
    client = httpx.AsyncClient(transport=httpx.MockTransport(respond))
    connector = SearXNGSearchConnector(base_url="http://searxng:8080", client=client)

    with pytest.raises(SearchServiceException) as raised:
        await connector.search("dinner")

    assert raised.value.reason == "service_unavailable"


def _searxng_answering(payload: dict, calls: list):
    """A SearXNG that always gives the same JSON and counts how often it was asked."""

    def respond(request: httpx.Request) -> httpx.Response:
        calls.append(request.url.params.get("q"))
        return httpx.Response(200, json=payload)

    return httpx.AsyncClient(transport=httpx.MockTransport(respond))


THROTTLED = {
    "results": [],
    "unresponsive_engines": [["brave", "too many requests"], ["duckduckgo", "CAPTCHA"]],
}


@pytest.mark.asyncio
async def test_searxng_with_every_engine_refusing_is_a_failure_that_says_why():
    """No results because the engines refused is not 'nothing matched': the model must be told (#42)."""
    connector = SearXNGSearchConnector(client=_searxng_answering(THROTTLED, []))

    with pytest.raises(SearchServiceException) as raised:
        await connector.search("Odebrecht Peru Lava Jato convictions")

    assert raised.value.reason == "throttled"
    assert "brave: too many requests" in raised.value.message
    assert "duckduckgo: CAPTCHA" in raised.value.message


@pytest.mark.asyncio
async def test_searxng_results_despite_some_refusing_engines_are_results():
    payload = {
        "results": [{"title": "Lava Jato", "url": "https://example.org/lj", "content": "The case"}],
        "unresponsive_engines": [["brave", "too many requests"]],
    }
    connector = SearXNGSearchConnector(client=_searxng_answering(payload, []))

    res = await connector.search("lava jato")

    assert [r.url for r in res.results] == ["https://example.org/lj"]


@pytest.mark.asyncio
async def test_searxng_with_nothing_matching_is_still_an_empty_search():
    connector = SearXNGSearchConnector(client=_searxng_answering({"results": [], "unresponsive_engines": []}, []))

    res = await connector.search("xyzzy plugh")

    assert res.results == []


class FakeClock:
    def __init__(self) -> None:
        self.now = 1000.0

    def __call__(self) -> float:
        return self.now


@pytest.mark.asyncio
async def test_searxng_while_throttled_fails_fast_without_asking_the_engines_again():
    """Retrying while the engines are blocking us only deepens the block (#42)."""
    calls: list = []
    clock = FakeClock()
    connector = SearXNGSearchConnector(
        client=_searxng_answering(THROTTLED, calls), throttle_cooldown_seconds=300, clock=clock
    )
    with pytest.raises(SearchServiceException):
        await connector.search("peru")

    clock.now += 299
    with pytest.raises(SearchServiceException) as raised:
        await connector.search("another query", fresh=True)

    assert raised.value.reason == "throttled"
    assert "brave: too many requests" in raised.value.message
    assert calls == ["peru"]


@pytest.mark.asyncio
async def test_searxng_asks_the_engines_again_once_the_cooldown_is_over():
    calls: list = []
    clock = FakeClock()
    connector = SearXNGSearchConnector(
        client=_searxng_answering(THROTTLED, calls), throttle_cooldown_seconds=300, clock=clock
    )
    with pytest.raises(SearchServiceException):
        await connector.search("peru")

    clock.now += 300
    with pytest.raises(SearchServiceException):
        await connector.search("peru")

    assert calls == ["peru", "peru"]


@pytest.mark.asyncio
async def test_searxng_while_throttled_still_answers_from_the_cache():
    calls: list = []
    answers = iter([
        {"results": [{"title": "Peru", "url": "https://example.org/pe", "content": ""}]},
        THROTTLED,
    ])

    def respond(request: httpx.Request) -> httpx.Response:
        calls.append(request.url.params.get("q"))
        return httpx.Response(200, json=next(answers))

    connector = SearXNGSearchConnector(
        client=httpx.AsyncClient(transport=httpx.MockTransport(respond)), clock=FakeClock()
    )
    await connector.search("peru")
    with pytest.raises(SearchServiceException):
        await connector.search("chile")

    res = await connector.search("peru")

    assert res.is_cached
    assert calls == ["peru", "chile"]


@pytest.mark.asyncio
async def test_searxng_throttled_general_engines_do_not_stop_a_search_of_other_engines():
    """Brave and DuckDuckGo turning us away says nothing about arXiv."""
    calls: list = []
    connector = SearXNGSearchConnector(client=_searxng_answering(THROTTLED, calls), clock=FakeClock())
    with pytest.raises(SearchServiceException):
        await connector.search("peru")

    with pytest.raises(SearchServiceException):
        await connector.search("peru", category="science", engines=["arxiv"])

    assert calls == ["peru", "peru"]
