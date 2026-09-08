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
    await asyncio.sleep(0.01)

    with pytest.raises(HTTPException) as exc_info:
        async with registry.acquire("sess-1"):
            pass

    assert exc_info.value.status_code == 409
    assert "currently processing another message" in exc_info.value.detail

    await task


@pytest.mark.asyncio
async def test_session_lock_try_acquire_and_release_prunes_memory():
    registry = SessionLockRegistry()

    # 1. try_acquire returns True when unlocked
    assert await registry.try_acquire("sess-1") is True
    assert "sess-1" in registry._locks

    # 2. try_acquire returns False when already locked
    assert await registry.try_acquire("sess-1") is False

    # 3. release unlocks and prunes the dictionary to prevent memory leaks
    await registry.release("sess-1")
    assert "sess-1" not in registry._locks

    # 4. Next acquire succeeds cleanly
    assert await registry.try_acquire("sess-1") is True
    await registry.release("sess-1")
    assert "sess-1" not in registry._locks


@pytest.mark.asyncio
async def test_session_lock_context_manager_prunes_on_exit():
    registry = SessionLockRegistry()

    async with registry.acquire("sess-prune"):
        assert "sess-prune" in registry._locks

    # When context manager exits, key must be pruned
    assert "sess-prune" not in registry._locks
