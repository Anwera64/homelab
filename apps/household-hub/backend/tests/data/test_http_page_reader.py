import httpx
import pytest

from app.domain.exceptions import PageReadException
from app.data.connectors.http_page_reader import HttpPageReader


ARTICLE_HTML = """<!DOCTYPE html>
<html lang="en"><head><title>Example Article Title</title><meta charset="utf-8"></head>
<body>
<header><nav>Home About Contact</nav></header>
<article>
<h1>Example Article Title</h1>
<p>This is the first paragraph of a readable article about something interesting that happened recently in the world of technology and science.</p>
<p>This is the second paragraph, continuing the discussion with more detail and context so that the extractor has enough content density to consider this a real article rather than boilerplate.</p>
<p>A third paragraph wraps up the piece with a conclusion and some final thoughts for the reader to consider going forward.</p>
</article>
<footer>Copyright 2026</footer>
</body></html>"""

EMPTY_HTML = "<html><head><title>Nothing here</title></head><body></body></html>"


def make_resolve(mapping):
    """Builds a fake `resolve(host) -> List[str]` that never touches real DNS."""

    async def _resolve(host):
        return mapping[host]

    return _resolve


def html_response(html: str = ARTICLE_HTML, status_code: int = 200, content_type: str = "text/html; charset=utf-8"):
    return httpx.Response(status_code, headers={"content-type": content_type}, content=html.encode("utf-8"))


@pytest.mark.asyncio
async def test_GIVEN_a_public_article_page_WHEN_read_THEN_title_and_body_text_are_extracted():
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.headers.get("host") == "public.example.com"
        return html_response()

    transport = httpx.MockTransport(handler)
    client = httpx.AsyncClient(transport=transport, follow_redirects=False)
    reader = HttpPageReader(client=client, resolve=make_resolve({"public.example.com": ["93.184.216.34"]}))

    page = await reader.read("http://public.example.com/article")

    assert page.url == "http://public.example.com/article"
    assert page.title == "Example Article Title"
    assert "first paragraph" in page.text
    assert "second paragraph" in page.text


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "address",
    ["10.0.0.5", "127.0.0.1", "169.254.169.254", "172.18.0.3", "::1"],
)
async def test_GIVEN_a_host_resolving_to_a_non_public_address_WHEN_read_THEN_refused_without_any_request(address):
    calls = []

    def handler(request: httpx.Request) -> httpx.Response:
        calls.append(request)
        return html_response()

    transport = httpx.MockTransport(handler)
    client = httpx.AsyncClient(transport=transport, follow_redirects=False)
    reader = HttpPageReader(client=client, resolve=make_resolve({"internal.example.com": [address]}))

    with pytest.raises(PageReadException):
        await reader.read("http://internal.example.com/secret")

    assert calls == []


@pytest.mark.asyncio
async def test_GIVEN_an_ip_literal_private_url_WHEN_read_THEN_refused():
    calls = []

    def handler(request: httpx.Request) -> httpx.Response:
        calls.append(request)
        return html_response()

    transport = httpx.MockTransport(handler)
    client = httpx.AsyncClient(transport=transport, follow_redirects=False)
    reader = HttpPageReader(client=client, resolve=make_resolve({}))

    with pytest.raises(PageReadException):
        await reader.read("http://127.0.0.1:9000/admin")

    assert calls == []


@pytest.mark.asyncio
async def test_GIVEN_a_redirect_from_a_public_host_to_a_private_host_WHEN_read_THEN_refused():
    def handler(request: httpx.Request) -> httpx.Response:
        if request.headers.get("host") == "public.example.com":
            return httpx.Response(302, headers={"location": "http://internal.example.com/steal"})
        return html_response()

    transport = httpx.MockTransport(handler)
    client = httpx.AsyncClient(transport=transport, follow_redirects=False)
    reader = HttpPageReader(
        client=client,
        resolve=make_resolve({"public.example.com": ["93.184.216.34"], "internal.example.com": ["10.0.0.5"]}),
    )

    with pytest.raises(PageReadException):
        await reader.read("http://public.example.com/link")


@pytest.mark.asyncio
async def test_GIVEN_a_redirect_to_another_public_host_WHEN_read_THEN_followed_and_final_url_returned():
    def handler(request: httpx.Request) -> httpx.Response:
        if request.headers.get("host") == "first.example.com":
            return httpx.Response(301, headers={"location": "https://second.example.com/moved"})
        assert request.headers.get("host") == "second.example.com"
        return html_response()

    transport = httpx.MockTransport(handler)
    client = httpx.AsyncClient(transport=transport, follow_redirects=False)
    reader = HttpPageReader(
        client=client,
        resolve=make_resolve({"first.example.com": ["93.184.216.34"], "second.example.com": ["93.184.216.35"]}),
    )

    page = await reader.read("http://first.example.com/old")

    assert page.url == "https://second.example.com/moved"
    assert page.title == "Example Article Title"


@pytest.mark.asyncio
async def test_GIVEN_a_vetted_public_ip_WHEN_read_THEN_the_request_is_pinned_to_that_ip_with_the_original_host_and_sni():
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url_host"] = request.url.host
        captured["host_header"] = request.headers.get("host")
        captured["sni_hostname"] = request.extensions.get("sni_hostname")
        return html_response()

    transport = httpx.MockTransport(handler)
    client = httpx.AsyncClient(transport=transport, follow_redirects=False)
    reader = HttpPageReader(client=client, resolve=make_resolve({"public.example.com": ["93.184.216.34"]}))

    page = await reader.read("https://public.example.com/article")

    # The check resolved "public.example.com" to 93.184.216.34 and vetted it; the actual
    # connection must go to that same address rather than re-resolving (which a hostile DNS
    # server could answer differently the second time -- a DNS-rebinding SSRF bypass).
    assert captured["url_host"] == "93.184.216.34"
    assert captured["host_header"] == "public.example.com"
    assert captured["sni_hostname"] == "public.example.com"
    assert page.url == "https://public.example.com/article"


@pytest.mark.asyncio
async def test_GIVEN_a_non_http_scheme_WHEN_read_THEN_refused():
    reader = HttpPageReader(resolve=make_resolve({}))

    with pytest.raises(PageReadException):
        await reader.read("ftp://public.example.com/file")


@pytest.mark.asyncio
async def test_GIVEN_a_404_response_WHEN_read_THEN_exception_is_raised():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(404, headers={"content-type": "text/html"}, content=b"<html>not found</html>")

    transport = httpx.MockTransport(handler)
    client = httpx.AsyncClient(transport=transport, follow_redirects=False)
    reader = HttpPageReader(client=client, resolve=make_resolve({"public.example.com": ["93.184.216.34"]}))

    with pytest.raises(PageReadException):
        await reader.read("http://public.example.com/missing")


@pytest.mark.asyncio
async def test_GIVEN_a_pdf_content_type_WHEN_read_THEN_exception_is_raised():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, headers={"content-type": "application/pdf"}, content=b"%PDF-1.4 ...")

    transport = httpx.MockTransport(handler)
    client = httpx.AsyncClient(transport=transport, follow_redirects=False)
    reader = HttpPageReader(client=client, resolve=make_resolve({"public.example.com": ["93.184.216.34"]}))

    with pytest.raises(PageReadException):
        await reader.read("http://public.example.com/file.pdf")


@pytest.mark.asyncio
async def test_GIVEN_a_page_with_no_readable_text_WHEN_read_THEN_exception_is_raised():
    def handler(request: httpx.Request) -> httpx.Response:
        return html_response(html=EMPTY_HTML)

    transport = httpx.MockTransport(handler)
    client = httpx.AsyncClient(transport=transport, follow_redirects=False)
    reader = HttpPageReader(client=client, resolve=make_resolve({"public.example.com": ["93.184.216.34"]}))

    with pytest.raises(PageReadException):
        await reader.read("http://public.example.com/blank")


@pytest.mark.asyncio
async def test_GIVEN_too_many_redirects_WHEN_read_THEN_exception_is_raised():
    def handler(request: httpx.Request) -> httpx.Response:
        n = int(request.url.path.strip("/").split("/")[-1] or 0)
        return httpx.Response(302, headers={"location": f"http://public.example.com/{n + 1}"})

    transport = httpx.MockTransport(handler)
    client = httpx.AsyncClient(transport=transport, follow_redirects=False)
    reader = HttpPageReader(
        client=client,
        resolve=make_resolve({"public.example.com": ["93.184.216.34"]}),
        max_redirects=3,
    )

    with pytest.raises(PageReadException):
        await reader.read("http://public.example.com/0")


@pytest.mark.asyncio
async def test_GIVEN_dns_resolution_fails_WHEN_read_THEN_exception_is_raised():
    async def failing_resolve(host):
        raise OSError(f"Name or service not known: {host}")

    reader = HttpPageReader(resolve=failing_resolve)

    with pytest.raises(PageReadException):
        await reader.read("http://unresolvable.example.com/x")


@pytest.mark.asyncio
async def test_GIVEN_a_large_page_WHEN_read_THEN_body_is_capped_at_max_bytes():
    big_paragraph = "<p>" + ("word " * 200000) + "</p>"
    big_html = f"<html><head><title>Big</title></head><body><article>{big_paragraph}</article></body></html>"

    def handler(request: httpx.Request) -> httpx.Response:
        return html_response(html=big_html)

    transport = httpx.MockTransport(handler)
    client = httpx.AsyncClient(transport=transport, follow_redirects=False)
    reader = HttpPageReader(
        client=client,
        resolve=make_resolve({"public.example.com": ["93.184.216.34"]}),
        max_bytes=1000,
    )

    page = await reader.read("http://public.example.com/huge")

    assert len(page.text) < len(big_html)
