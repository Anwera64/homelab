import pytest
import httpx

@pytest.mark.asyncio
async def test_healthcheck_returns_healthy(client: httpx.AsyncClient):
    """Healthcheck endpoint must return 200 and healthy DB status."""
    response = await client.get("/api/v1/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"
    assert data["database"] == "connected"
    assert "version" in data
