import io
import pytest
import httpx
from unittest.mock import patch, AsyncMock, MagicMock
from app.domain.entities.calendar_event import CalendarEvent
from app.domain.entities.search_result import SearchResult, SearchResultItem
from tests.auth_helpers import ADMIN_PIN, add_signed_in_member, register_admin, sign_in


async def create_authenticated_user(client: httpx.AsyncClient) -> str:
    """Helper to create the initial admin, or sign them in again, and return a JWT access token."""
    status_resp = await client.get("/api/v1/auth/status")
    if not status_resp.json()["is_initialized"]:
        token, _ = await register_admin(client, full_name="Test User")
        return token
    members = (await client.get("/api/v1/auth/members")).json()
    return await sign_in(client, members[0]["id"], ADMIN_PIN)


@pytest.mark.asyncio
async def test_list_tools_endpoint(client: httpx.AsyncClient):
    token = await create_authenticated_user(client)
    headers = {"Authorization": f"Bearer {token}"}

    response = await client.get("/api/v1/integrations/tools", headers=headers)
    assert response.status_code == 200
    data = response.json()
    assert isinstance(data, list)
    assert len(data) == 5
    names = [t["name"] for t in data]
    assert "calendar_read" in names
    assert "calendar_write" in names
    assert "searxng_search" in names
    assert "pdf_reader" in names
    assert "document_writer" in names


@pytest.mark.asyncio
async def test_tool_execution_endpoint_and_soft_degradation(client: httpx.AsyncClient):
    token = await create_authenticated_user(client)
    headers = {"Authorization": f"Bearer {token}"}

    # Fetch assistant id
    agents_resp = await client.get("/api/v1/agents", headers=headers)
    assistant_id = next(a["id"] for a in agents_resp.json() if a["slug"] == "assistant")

    # Create normal session and secret session
    sess_norm_resp = await client.post(
        "/api/v1/sessions",
        headers=headers,
        json={"title": "Normal Chat", "agent_id": assistant_id, "is_secret": False},
    )
    norm_session_id = sess_norm_resp.json()["id"]

    sess_sec_resp = await client.post(
        "/api/v1/sessions",
        headers=headers,
        json={"title": "Secret Chat", "agent_id": assistant_id, "is_secret": True},
    )
    sec_session_id = sess_sec_resp.json()["id"]

    # 1. Unknown tool
    resp = await client.post(
        "/api/v1/integrations/tools/execute",
        headers=headers,
        json={
            "session_id": norm_session_id,
            "tool_name": "unknown_tool",
            "arguments": {},
        },
    )
    assert resp.status_code == 403  # Tool not in agent permissions

    # 2. Unconfigured calendar soft degradation
    resp_cal = await client.post(
        "/api/v1/integrations/tools/execute",
        headers=headers,
        json={
            "session_id": norm_session_id,
            "tool_name": "calendar_read",
            "arguments": {
                "start_time": "2026-09-08T00:00:00Z",
                "end_time": "2026-09-09T00:00:00Z",
            },
        },
    )
    assert resp_cal.status_code == 200
    data = resp_cal.json()
    assert data["success"] is False
    assert "No calendar configured" in data["error"]

    # 3. Secret Mode lock check
    resp_secret = await client.post(
        "/api/v1/integrations/tools/execute",
        headers=headers,
        json={
            "session_id": sec_session_id,
            "tool_name": "calendar_write",
            "arguments": {
                "action": "create",
                "title": "Secret Event",
                "start_time": "2026-09-08T10:00:00Z",
                "end_time": "2026-09-08T11:00:00Z",
            },
        },
    )
    assert resp_secret.status_code == 200
    data_secret = resp_secret.json()
    assert data_secret["success"] is False
    assert "Secret Mode" in data_secret["error"]


@pytest.mark.asyncio
async def test_tool_execution_server_authoritative_session_controls(client: httpx.AsyncClient):
    token = await create_authenticated_user(client)
    headers = {"Authorization": f"Bearer {token}"}

    # Fetch built-in assistant id
    agents_resp = await client.get("/api/v1/agents", headers=headers)
    assistant_id = next(a["id"] for a in agents_resp.json() if a["slug"] == "assistant")

    # 1. Create a session with is_secret=True on the server
    sess_resp = await client.post(
        "/api/v1/sessions",
        headers=headers,
        json={"title": "Private Plan", "agent_id": assistant_id, "is_secret": True},
    )
    assert sess_resp.status_code == 201
    secret_session_id = sess_resp.json()["id"]

    # Client attempts to bypass secret mode by passing is_secret_mode: False
    spoof_secret_resp = await client.post(
        "/api/v1/integrations/tools/execute",
        headers=headers,
        json={
            "session_id": secret_session_id,
            "tool_name": "calendar_write",
            "arguments": {
                "action": "create",
                "title": "Sneaky Event",
                "start_time": "2026-09-08T10:00:00Z",
                "end_time": "2026-09-08T11:00:00Z",
            },
            "is_secret_mode": False,
        },
    )
    assert spoof_secret_resp.status_code == 200
    spoof_secret_data = spoof_secret_resp.json()
    assert spoof_secret_data["success"] is False
    assert "Secret Mode" in spoof_secret_data["error"]

    # 2. Create a custom agent with restricted permissions: only ["calendar_read"]
    agent_resp = await client.post(
        "/api/v1/agents",
        headers=headers,
        json={
            "slug": "calendar-only-agent",
            "name": "Calendar Only Agent",
            "description": "Agent that can only read calendar",
            "system_prompt": "You are a calendar assistant.",
            "tool_permissions": ["calendar_read"],
        },
    )
    assert agent_resp.status_code == 201
    agent_id = agent_resp.json()["id"]

    agent_sess_resp = await client.post(
        "/api/v1/sessions",
        headers=headers,
        json={"title": "Cal Agent Chat", "agent_id": agent_id},
    )
    assert agent_sess_resp.status_code == 201
    agent_sess_id = agent_sess_resp.json()["id"]

    # Client attempts to run searxng_search by spoofing agent_slug="researcher"
    spoof_agent_resp = await client.post(
        "/api/v1/integrations/tools/execute",
        headers=headers,
        json={
            "session_id": agent_sess_id,
            "tool_name": "searxng_search",
            "arguments": {"query": "Quantum computing"},
            "agent_slug": "researcher",
        },
    )
    # Must be 403 Forbidden because server resolves session.agent_id which lacks searxng_search
    assert spoof_agent_resp.status_code == 403

    # 3. Create a second user and verify Zero-Leak cross-user session blocking
    u2_token, _ = await add_signed_in_member(client, full_name="Member User")
    u2_headers = {"Authorization": f"Bearer {u2_token}"}

    u2_sess = await client.post(
        "/api/v1/sessions",
        headers=u2_headers,
        json={"title": "User 2 Secret Chat", "agent_id": assistant_id},
    )
    assert u2_sess.status_code == 201
    u2_sess_id = u2_sess.json()["id"]

    # User 1 attempts to execute tool against User 2's session
    cross_user_resp = await client.post(
        "/api/v1/integrations/tools/execute",
        headers=headers,
        json={
            "session_id": u2_sess_id,
            "tool_name": "calendar_read",
            "arguments": {
                "start_time": "2026-09-08T00:00:00Z",
                "end_time": "2026-09-09T00:00:00Z",
            },
        },
    )
    assert cross_user_resp.status_code == 403

    # 4. Archived session rejects tool execution
    # Delete and purge the custom agent so its session is archived
    del_agent_resp = await client.delete(f"/api/v1/agents/{agent_id}", headers=headers)
    assert del_agent_resp.status_code == 200
    purge_resp = await client.delete(f"/api/v1/agents/trash/{agent_id}", headers=headers)
    assert purge_resp.status_code == 200

    archived_tool_resp = await client.post(
        "/api/v1/integrations/tools/execute",
        headers=headers,
        json={
            "session_id": agent_sess_id,
            "tool_name": "calendar_read",
            "arguments": {
                "start_time": "2026-09-08T00:00:00Z",
                "end_time": "2026-09-09T00:00:00Z",
            },
        },
    )
    assert archived_tool_resp.status_code == 400
    assert "archived" in archived_tool_resp.json()["detail"].lower()



@pytest.mark.asyncio
async def test_calendar_crud_and_safety_endpoints(client: httpx.AsyncClient):
    token = await create_authenticated_user(client)
    headers = {"Authorization": f"Bearer {token}"}

    with patch("app.data.connectors.caldav_calendar_connector.CalDavCalendarConnector.test_connection", return_value=True):
        # 1. Configure calendar
        conf_resp = await client.post(
            "/api/v1/integrations/calendars",
            headers=headers,
            json={
                "provider": "apple_icloud",
                "url": "https://caldav.icloud.com",
                "username": "user@icloud.com",
                "password": "app-specific-pwd",
                "calendar_name": "Home",
            },
        )
        assert conf_resp.status_code == 201
        conf_data = conf_resp.json()
        assert conf_data["provider"] == "apple_icloud"
        assert conf_data["calendar_name"] == "Home"
        assert "password" not in conf_data
        assert "encrypted_secret" not in conf_data

        # 2. Get my calendar
        get_resp = await client.get("/api/v1/integrations/calendars/me", headers=headers)
        assert get_resp.status_code == 200
        assert get_resp.json()["username"] == "user@icloud.com"

        # 3. Delete my calendar
        del_resp = await client.delete("/api/v1/integrations/calendars", headers=headers)
        assert del_resp.status_code == 204

        # 4. Get after delete -> 404
        get_after = await client.get("/api/v1/integrations/calendars/me", headers=headers)
        assert get_after.status_code == 404


@pytest.mark.asyncio
async def test_document_crud_and_export_endpoint(client: httpx.AsyncClient):
    token = await create_authenticated_user(client)
    headers = {"Authorization": f"Bearer {token}"}

    # 1. Create document
    create_resp = await client.post(
        "/api/v1/integrations/documents",
        headers=headers,
        json={
            "title": "Synthesis Paper",
            "content": "# Executive Summary\nKey findings on bioclimatic design.",
            "action": "create",
        },
    )
    assert create_resp.status_code == 201
    doc_data = create_resp.json()
    assert doc_data["title"] == "Synthesis Paper"
    assert doc_data["version"] == 1
    doc_id = doc_data["id"]

    # 2. Append to document
    append_resp = await client.post(
        "/api/v1/integrations/documents",
        headers=headers,
        json={
            "title": "Synthesis Paper",
            "content": "\n\n## Section 2\nAdditional thermal data.",
            "action": "append",
        },
    )
    assert append_resp.status_code == 201
    assert append_resp.json()["version"] == 2

    # 3. Retrieve document by id
    get_resp = await client.get(f"/api/v1/integrations/documents/{doc_id}", headers=headers)
    assert get_resp.status_code == 200
    assert "Section 2" in get_resp.json()["content"]

    # 4. Export as Markdown (.md)
    export_resp = await client.get(f"/api/v1/integrations/documents/{doc_id}/export", headers=headers)
    assert export_resp.status_code == 200
    assert "text/markdown" in export_resp.headers["content-type"]
    assert "attachment; filename=" in export_resp.headers["content-disposition"]
    assert "Section 2" in export_resp.text


@pytest.mark.asyncio
async def test_pdf_upload_streaming_size_limit_and_empty_check(client: httpx.AsyncClient):
    token = await create_authenticated_user(client)
    headers = {"Authorization": f"Bearer {token}"}

    # 1. Empty file -> 400 Bad Request
    empty_file = ("empty.pdf", io.BytesIO(b""), "application/pdf")
    resp_empty = await client.post(
        "/api/v1/integrations/documents/pdf",
        headers=headers,
        files={"file": empty_file},
    )
    assert resp_empty.status_code == 400
    assert "empty" in resp_empty.json()["detail"].lower()

    # 2. Oversized file -> 413 Request Entity Too Large
    with patch("app.core.config.settings.MAX_PDF_SIZE_BYTES", 500):
        oversized_bytes = b"%PDF-1.4 " + (b"A" * 1000)
        oversized_file = ("large.pdf", io.BytesIO(oversized_bytes), "application/pdf")
        resp_oversized = await client.post(
            "/api/v1/integrations/documents/pdf",
            headers=headers,
            files={"file": oversized_file},
        )
        assert resp_oversized.status_code == 413
        assert "exceeds maximum limit" in resp_oversized.json()["detail"].lower()


@pytest.mark.asyncio
async def test_tool_execution_requires_session_id(client: httpx.AsyncClient):
    token = await create_authenticated_user(client)
    headers = {"Authorization": f"Bearer {token}"}

    # Attempting to execute tool without session_id must fail with 422 Unprocessable Entity
    resp = await client.post(
        "/api/v1/integrations/tools/execute",
        headers=headers,
        json={
            "tool_name": "calendar_read",
            "arguments": {
                "start_time": "2026-09-08T00:00:00Z",
                "end_time": "2026-09-09T00:00:00Z",
            },
        },
    )
    assert resp.status_code == 422


@pytest.mark.asyncio
async def test_caldav_url_ssrf_and_scheme_validation(client: httpx.AsyncClient):
    token = await create_authenticated_user(client)
    headers = {"Authorization": f"Bearer {token}"}

    # 1. Invalid scheme (ftp://) -> 422
    resp_ftp = await client.post(
        "/api/v1/integrations/calendars",
        headers=headers,
        json={
            "provider": "caldav",
            "url": "ftp://caldav.example.com",
            "username": "user",
            "password": "pwd",
        },
    )
    assert resp_ftp.status_code == 422

    # 2. Cloud metadata IP SSRF block -> 422
    resp_metadata = await client.post(
        "/api/v1/integrations/calendars",
        headers=headers,
        json={
            "provider": "caldav",
            "url": "http://169.254.169.254/latest/meta-data/",
            "username": "user",
            "password": "pwd",
        },
    )
    assert resp_metadata.status_code == 422


@pytest.mark.asyncio
async def test_integration_input_max_length_validation(client: httpx.AsyncClient):
    token = await create_authenticated_user(client)
    headers = {"Authorization": f"Bearer {token}"}

    # 1. Search query > 1000 chars -> 422
    resp_search = await client.post(
        "/api/v1/integrations/search",
        headers=headers,
        json={"query": "X" * 1001},
    )
    assert resp_search.status_code == 422

    # 2. Document title > 255 chars -> 422
    resp_doc = await client.post(
        "/api/v1/integrations/documents",
        headers=headers,
        json={"title": "T" * 256, "content": "Content"},
    )
    assert resp_doc.status_code == 422

    # 3. Calendar event title > 255 chars -> 422
    resp_event = await client.post(
        "/api/v1/integrations/calendars/events",
        headers=headers,
        json={
            "title": "E" * 256,
            "start_time": "2026-09-08T10:00:00Z",
            "end_time": "2026-09-08T11:00:00Z",
        },
    )
    assert resp_event.status_code == 422


@pytest.mark.asyncio
async def test_calendar_events_corrupted_secret_returns_409(client: httpx.AsyncClient, db_session):
    from sqlalchemy import update
    from app.data.models.calendar_credential_model import CalendarCredentialModel

    token = await create_authenticated_user(client)
    headers = {"Authorization": f"Bearer {token}"}

    with patch("app.data.connectors.caldav_calendar_connector.CalDavCalendarConnector.test_connection", return_value=True):
        await client.post(
            "/api/v1/integrations/calendars",
            headers=headers,
            json={
                "provider": "apple_icloud",
                "url": "https://caldav.icloud.com",
                "username": "cipher@icloud.com",
                "password": "valid-app-password",
            },
        )

    # Corrupt the encrypted secret in DB to simulate key rotation or data corruption
    await db_session.execute(
        update(CalendarCredentialModel)
        .values(encrypted_secret="corrupted_invalid_token_value")
    )
    await db_session.commit()

    # Calling fetch events should return 409 Conflict (not 500, and not 401 -- the token itself is fine)
    resp = await client.get(
        "/api/v1/integrations/calendars/events",
        headers=headers,
        params={"start_time": "2026-09-08T00:00:00Z", "end_time": "2026-09-09T00:00:00Z"},
    )
    assert resp.status_code == 409
    assert "decrypted" in resp.json()["detail"].lower()
    assert resp.json()["code"] == "calendar_unreadable"


