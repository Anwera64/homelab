import pytest
import pytest_asyncio
import httpx
from fastapi import FastAPI

from app.presentation.api.exception_handlers import register_exception_handlers
from app.domain.exceptions import (
    WrongPinException,
    WrongConfirmationPinException,
    PinLockedException,
    InviteInvalidException,
    NameTakenException,
    CodeGuessesLockedException,
    SoleAdminDeletionException,
    OwnPinResetException,
    SecretDecryptionException,
)


def build_app() -> FastAPI:
    """A tiny FastAPI app wired the same way as the real app: one route per exception."""
    app = FastAPI()
    register_exception_handlers(app)

    @app.get("/wrong-pin")
    async def _wrong_pin():
        raise WrongPinException(attempts_left=2)

    @app.get("/wrong-confirmation-pin")
    async def _wrong_confirmation_pin():
        raise WrongConfirmationPinException(attempts_left=1)

    @app.get("/pin-locked")
    async def _pin_locked():
        raise PinLockedException(retry_after_seconds=30)

    @app.get("/invite-invalid")
    async def _invite_invalid():
        raise InviteInvalidException("This invite code is invalid, used, or expired.")

    @app.get("/name-taken")
    async def _name_taken():
        raise NameTakenException("That name is already taken.")

    @app.get("/code-guesses-locked")
    async def _code_guesses_locked():
        raise CodeGuessesLockedException(retry_after_seconds=60)

    @app.get("/sole-admin")
    async def _sole_admin():
        raise SoleAdminDeletionException("Cannot delete the only administrator.")

    @app.get("/own-pin-reset")
    async def _own_pin_reset():
        raise OwnPinResetException("You cannot approve your own PIN reset.")

    @app.get("/secret-decryption")
    async def _secret_decryption():
        raise SecretDecryptionException("Stored secret could not be decrypted.")

    return app


@pytest_asyncio.fixture
async def handler_client():
    app = build_app()
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://testserver") as ac:
        yield ac


@pytest.mark.asyncio
async def test_a_wrong_pin_is_401_with_attempts_left(handler_client: httpx.AsyncClient):
    resp = await handler_client.get("/wrong-pin")
    assert resp.status_code == 401
    body = resp.json()
    assert body["detail"]
    assert body["code"] == "wrong_pin"
    assert body["attempts_left"] == 2


@pytest.mark.asyncio
async def test_a_wrong_confirmation_pin_is_403_with_attempts_left(handler_client: httpx.AsyncClient):
    resp = await handler_client.get("/wrong-confirmation-pin")
    assert resp.status_code == 403
    body = resp.json()
    assert body["detail"]
    assert body["code"] == "wrong_pin"
    assert body["attempts_left"] == 1


@pytest.mark.asyncio
async def test_a_pin_lockout_is_429_with_retry_after(handler_client: httpx.AsyncClient):
    resp = await handler_client.get("/pin-locked")
    assert resp.status_code == 429
    body = resp.json()
    assert body["detail"]
    assert body["code"] == "pin_locked"
    assert body["retry_after_seconds"] == 30
    assert resp.headers.get("retry-after") == "30"


@pytest.mark.asyncio
async def test_an_invalid_invite_is_400(handler_client: httpx.AsyncClient):
    resp = await handler_client.get("/invite-invalid")
    assert resp.status_code == 400
    body = resp.json()
    assert body["detail"]
    assert body["code"] == "invite_invalid"


@pytest.mark.asyncio
async def test_a_taken_name_is_409(handler_client: httpx.AsyncClient):
    resp = await handler_client.get("/name-taken")
    assert resp.status_code == 409
    body = resp.json()
    assert body["detail"]
    assert body["code"] == "name_taken"


@pytest.mark.asyncio
async def test_too_many_wrong_codes_is_429_with_retry_after(handler_client: httpx.AsyncClient):
    resp = await handler_client.get("/code-guesses-locked")
    assert resp.status_code == 429
    body = resp.json()
    assert body["detail"]
    assert body["code"] == "code_guesses_locked"
    assert body["retry_after_seconds"] == 60
    assert resp.headers.get("retry-after") == "60"


@pytest.mark.asyncio
async def test_deleting_the_sole_admin_is_409(handler_client: httpx.AsyncClient):
    resp = await handler_client.get("/sole-admin")
    assert resp.status_code == 409
    body = resp.json()
    assert body["detail"]
    assert body["code"] == "sole_admin"


@pytest.mark.asyncio
async def test_approving_your_own_pin_reset_is_400(handler_client: httpx.AsyncClient):
    resp = await handler_client.get("/own-pin-reset")
    assert resp.status_code == 400
    body = resp.json()
    assert body["detail"]
    assert body["code"] == "own_pin_reset"


@pytest.mark.asyncio
async def test_an_undecryptable_calendar_secret_is_409(handler_client: httpx.AsyncClient):
    resp = await handler_client.get("/secret-decryption")
    assert resp.status_code == 409
    body = resp.json()
    assert body["detail"] == "Stored credentials could not be decrypted. Please reconfigure your calendar."
    assert body["code"] == "calendar_unreadable"
