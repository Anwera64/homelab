import pytest
import httpx


@pytest.mark.asyncio
async def test_cors_allows_configured_duckdns_origin(client: httpx.AsyncClient):
    """Assert preflight from https://spicy-llama.duckdns.org receives allow-origin and credentials."""
    resp = await client.options(
        "/api/v1/auth/login",
        headers={
            "Origin": "https://spicy-llama.duckdns.org",
            "Access-Control-Request-Method": "POST",
            "Access-Control-Request-Headers": "Authorization,Content-Type",
        },
    )
    assert resp.status_code == 200
    assert resp.headers.get("access-control-allow-origin") == "https://spicy-llama.duckdns.org"
    assert resp.headers.get("access-control-allow-credentials") == "true"


@pytest.mark.asyncio
async def test_cors_allows_duckdns_subdomain(client: httpx.AsyncClient):
    """Assert preflight from a DuckDNS subdomain receives allow-origin reflection and credentials."""
    resp = await client.options(
        "/api/v1/auth/login",
        headers={
            "Origin": "https://hub.spicy-llama.duckdns.org",
            "Access-Control-Request-Method": "POST",
            "Access-Control-Request-Headers": "Authorization,Content-Type",
        },
    )
    assert resp.status_code == 200
    assert resp.headers.get("access-control-allow-origin") == "https://hub.spicy-llama.duckdns.org"
    assert resp.headers.get("access-control-allow-credentials") == "true"


@pytest.mark.asyncio
async def test_cors_rejects_unauthorized_origin(client: httpx.AsyncClient):
    """Assert preflight from an unauthorized origin does not return access-control-allow-origin."""
    resp = await client.options(
        "/api/v1/auth/login",
        headers={
            "Origin": "https://malicious-attacker.com",
            "Access-Control-Request-Method": "POST",
            "Access-Control-Request-Headers": "Authorization,Content-Type",
        },
    )
    # When an origin is not allowed, Starlette's CORSMiddleware omits Access-Control-Allow-Origin
    assert resp.headers.get("access-control-allow-origin") is None
