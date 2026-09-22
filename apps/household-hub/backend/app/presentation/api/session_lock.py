import asyncio
from contextlib import asynccontextmanager
from typing import Dict
from fastapi import HTTPException, status


class SessionLockRegistry:
    """
    In-memory registry of locks keyed by session_id to guarantee that concurrent
    requests to the same conversation session are rejected with 409 Conflict.
    Automatically prunes locks upon release to prevent unbounded memory growth.
    """

    def __init__(self):
        self._locks: Dict[str, asyncio.Lock] = {}
        self._registry_guard = asyncio.Lock()

    async def try_acquire(self, session_id: str) -> bool:
        """
        Atomically attempts to acquire a lock on session_id.
        Returns True if acquired successfully, False if already locked.
        """
        async with self._registry_guard:
            if session_id not in self._locks:
                self._locks[session_id] = asyncio.Lock()
            lock = self._locks[session_id]

            if lock.locked():
                return False
            await lock.acquire()
            return True

    def is_locked(self, session_id: str) -> bool:
        """
        Whether a turn is being generated for this session right now.

        Read-only and synchronous on purpose: it takes no lock of its own, so a read can never
        queue behind the turn it is asking about. It is a snapshot — the turn may finish the
        instant after — which is all the client needs, because it polls.

        Per-process, like the registry it reads. Behind `uvicorn --workers N` a turn on one worker
        would look idle to another; the hub is single-process and has no worker configuration.
        """
        lock = self._locks.get(session_id)
        return lock is not None and lock.locked()

    async def release(self, session_id: str) -> None:
        """
        Releases the lock on session_id and removes it from the registry
        to prevent memory leaks.
        """
        async with self._registry_guard:
            if session_id in self._locks:
                lock = self._locks[session_id]
                if lock.locked():
                    lock.release()
                del self._locks[session_id]

    @asynccontextmanager
    async def acquire(self, session_id: str):
        """
        Context manager wrapper around try_acquire / release.
        Raises HTTPException 409 Conflict if lock cannot be acquired immediately.
        """
        if not await self.try_acquire(session_id):
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail=f"Session {session_id} is currently processing another message.",
            )
        try:
            yield
        finally:
            await self.release(session_id)
