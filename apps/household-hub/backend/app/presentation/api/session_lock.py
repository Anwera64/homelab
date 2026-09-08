import asyncio
from contextlib import asynccontextmanager
from typing import Dict
from fastapi import HTTPException, status


class SessionLockRegistry:
    """
    In-memory registry of locks keyed by session_id to guarantee that concurrent
    requests to the same conversation session are rejected with 409 Conflict.
    """

    def __init__(self):
        self._locks: Dict[str, asyncio.Lock] = {}
        self._registry_guard = asyncio.Lock()

    @asynccontextmanager
    async def acquire(self, session_id: str):
        async with self._registry_guard:
            if session_id not in self._locks:
                self._locks[session_id] = asyncio.Lock()
            lock = self._locks[session_id]

            if lock.locked():
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail=f"Session {session_id} is currently processing another message.",
                )
            await lock.acquire()

        try:
            yield
        finally:
            lock.release()
