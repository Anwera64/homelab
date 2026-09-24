import asyncio
import ipaddress
import re
from html import unescape
from typing import Awaitable, Callable, List, Optional, Union
from urllib.parse import urljoin, urlsplit, urlunsplit

import httpx
import trafilatura

from app.domain.entities.source_passage import WebPage
from app.domain.exceptions import PageReadException
from app.domain.repositories.page_reader import IPageReader

_TITLE_TAG_RE = re.compile(r"<title[^>]*>(.*?)</title>", re.IGNORECASE | re.DOTALL)
_ALLOWED_CONTENT_TYPES = ("text/html", "application/xhtml+xml")


async def _default_resolve(host: str) -> List[str]:
    """Resolves a hostname to its IP address strings via the running loop's DNS, so tests never
    need real DNS: they inject a fake `resolve` instead."""
    loop = asyncio.get_running_loop()
    try:
        infos = await loop.getaddrinfo(host, None)
    except OSError as e:
        raise PageReadException(f"Could not resolve host '{host}': {e}")
    addresses = {info[4][0] for info in infos}
    if not addresses:
        raise PageReadException(f"Could not resolve host '{host}': no addresses returned")
    return list(addresses)


class HttpPageReader(IPageReader):
    """
    Fetches a page over HTTP(S) and hands back its readable text.

    An LLM agent picks the URL for `read`, and the backend runs in Docker next to internal
    services (Ollama, SearXNG, LAN devices) -- so this must never become a way to reach them.
    Every hop, the first request and each redirect, is resolved and checked before it's dialed:
    anything that isn't a globally routable address (private, loopback, link-local, multicast,
    reserved, unspecified, or an IPv4-mapped IPv6 address wrapping one of those) is refused.
    """

    def __init__(
        self,
        client: Optional[httpx.AsyncClient] = None,
        resolve: Optional[Callable[[str], Awaitable[List[str]]]] = None,
        max_bytes: int = 2_000_000,
        max_redirects: int = 5,
    ):
        self._external_client = client
        self._internal_client: Optional[httpx.AsyncClient] = None
        self._resolve = resolve or _default_resolve
        self.max_bytes = max_bytes
        self.max_redirects = max_redirects

    async def _get_client(self) -> httpx.AsyncClient:
        if self._external_client:
            return self._external_client
        if self._internal_client is None or self._internal_client.is_closed:
            # Redirects are followed by hand, one hop at a time, so every hop's host can be
            # checked before it's dialed.
            self._internal_client = httpx.AsyncClient(follow_redirects=False)
        return self._internal_client

    async def close(self) -> None:
        """Gracefully closes the internal HTTP client connection pool if owned."""
        if self._internal_client is not None and not self._internal_client.is_closed:
            await self._internal_client.aclose()
        self._internal_client = None

    async def _vetted_ip(self, host: str) -> Union[ipaddress.IPv4Address, ipaddress.IPv6Address]:
        """Resolves (or parses) a host, raises unless every address it maps to is public, and
        returns one vetted address to connect to.

        The caller must dial this exact address rather than letting httpx re-resolve the
        hostname: a hostile DNS server could answer "public" for this check and "private" for
        the real connection (DNS rebinding), which would defeat the check entirely.
        """
        try:
            addresses = [ipaddress.ip_address(host)]
        except ValueError:
            try:
                resolved = await self._resolve(host)
            except PageReadException:
                raise
            except Exception as e:
                raise PageReadException(f"Could not resolve host '{host}': {e}")
            try:
                addresses = [ipaddress.ip_address(a) for a in resolved]
            except ValueError as e:
                raise PageReadException(f"Could not resolve host '{host}': {e}")

        for ip in addresses:
            mapped = ip.ipv4_mapped if isinstance(ip, ipaddress.IPv6Address) else None
            target = mapped if mapped is not None else ip
            if not target.is_global:
                raise PageReadException(
                    f"Refusing to fetch '{host}': resolves to non-public address {ip}"
                )

        return addresses[0]

    @staticmethod
    def _pin_url(url_parts, ip: Union[ipaddress.IPv4Address, ipaddress.IPv6Address]) -> str:
        """Rebuilds the request URL with its host replaced by the vetted IP, keeping the
        scheme/port/path/query/fragment intact, so the connection can't be re-resolved."""
        host_part = f"[{ip}]" if isinstance(ip, ipaddress.IPv6Address) else str(ip)
        netloc = f"{host_part}:{url_parts.port}" if url_parts.port else host_part
        return urlunsplit((url_parts.scheme, netloc, url_parts.path or "/", url_parts.query, url_parts.fragment))

    @staticmethod
    async def _extract_title(page_html: str, fallback_url: str) -> str:
        try:
            meta = await asyncio.to_thread(trafilatura.extract_metadata, page_html)
        except Exception:
            meta = None
        if meta and meta.title:
            return meta.title
        match = _TITLE_TAG_RE.search(page_html)
        if match:
            title = unescape(match.group(1)).strip()
            if title:
                return title
        return fallback_url

    async def read(self, url: str, timeout: float = 10.0) -> WebPage:
        client = await self._get_client()
        current_url = url

        for hop in range(self.max_redirects + 1):
            parts = urlsplit(current_url)
            if parts.scheme not in ("http", "https") or not parts.hostname:
                raise PageReadException(f"Unsupported URL: '{current_url}'")

            hostname = parts.hostname
            ip = await self._vetted_ip(hostname)
            pinned_url = self._pin_url(parts, ip)
            # The Host header and SNI hostname are the ORIGINAL hostname, not the IP: the server
            # still needs it for virtual hosting, and TLS still needs it for SNI and certificate
            # verification. Only the socket connects to the vetted IP.
            headers = {"Host": hostname}
            extensions = {"sni_hostname": hostname} if parts.scheme == "https" else {}

            try:
                async with client.stream(
                    "GET", pinned_url, timeout=timeout, headers=headers, extensions=extensions
                ) as response:
                    if 300 <= response.status_code < 400 and "location" in response.headers:
                        if hop >= self.max_redirects:
                            raise PageReadException(f"Too many redirects fetching '{url}'")
                        current_url = urljoin(current_url, response.headers["location"])
                        continue

                    if not (200 <= response.status_code < 300):
                        raise PageReadException(
                            f"'{current_url}' returned HTTP {response.status_code}"
                        )

                    content_type = response.headers.get("content-type", "")
                    mime = content_type.split(";")[0].strip().lower()
                    if mime not in _ALLOWED_CONTENT_TYPES:
                        raise PageReadException(
                            f"'{current_url}' is not a web page (content-type: {mime or 'unknown'})"
                        )

                    charset = "utf-8"
                    for param in content_type.split(";")[1:]:
                        param = param.strip()
                        if param.lower().startswith("charset="):
                            charset = param.split("=", 1)[1].strip().strip('"')

                    # Streamed and capped: a run-away or hostile page can't be used to exhaust
                    # memory. What's read before the cap is what gets extracted.
                    body = bytearray()
                    async for chunk in response.aiter_bytes():
                        body.extend(chunk)
                        if len(body) >= self.max_bytes:
                            break
                    body = bytes(body[: self.max_bytes])
            except httpx.HTTPError as e:
                raise PageReadException(f"Could not fetch '{current_url}': {e}")

            try:
                page_html = body.decode(charset, errors="replace")
            except LookupError:
                page_html = body.decode("utf-8", errors="replace")

            text = await asyncio.to_thread(
                trafilatura.extract, page_html, include_comments=False, include_tables=True
            )
            if not text or not text.strip():
                raise PageReadException(f"'{current_url}' has no readable text")

            title = await self._extract_title(page_html, current_url)
            return WebPage(url=current_url, title=title, text=text)

        raise PageReadException(f"Too many redirects fetching '{url}'")
