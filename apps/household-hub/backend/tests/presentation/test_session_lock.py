import asyncio
import pytest
from fastapi import HTTPException

from app.presentation.api.session_lock import SessionLockRegistry


@pytest.mark.asyncio
async def test_session_lock_allows_sequential_access():
    registry = SessionLockRegistry()

    async with registry.acquire("sess-1"):
        pass

    async with registry.acquire("sess-1"):
        pass


@pytest.mark.asyncio
async def test_session_lock_allows_concurrent_different_sessions():
    registry = SessionLockRegistry()

    async with registry.acquire("sess-1"):
        async with registry.acquire("sess-2"):
            pass


@pytest.mark.asyncio
async def test_session_lock_rejects_concurrent_same_session_with_409():
    registry = SessionLockRegistry()

    async def hold_lock():
        async with registry.acquire("sess-1"):
            await asyncio.sleep(0.1)

    task = asyncio.create_task(hold_lock())
    # Yield control to let task acquire the lock
    await asyncio.sleep(0.01)

    with pytest.raises(HTTPException) as exc_info:
        async with registry.acquire("sess-1"):
            pass

    assert exc_info.value.status_code == 409
    assert "currently processing another message" in exc_info.value.detail

    await task
