import asyncio
import logging
from typing import Awaitable, Callable, Dict, Set

logger = logging.getLogger(__name__)


class AfterTurnWork:
    """
    What a turn leaves to do once its answer is sent — reflection, then the history summary (#53).

    That work used to run inside the turn, while the chat's lock was held, so a member who wrote
    again soon after an answer was refused for as long as two more model calls took. It runs here
    instead, after the lock is let go. One chat's jobs run one at a time, in the order they came, so
    two summaries never fold the same messages at once; different chats don't wait for each other.

    It holds the tasks too: the event loop keeps only weak references, so a job started with a bare
    `create_task` could be collected halfway. Per-process, like `SessionLockRegistry`.
    """

    def __init__(self) -> None:
        self._locks: Dict[str, asyncio.Lock] = {}
        self._queued: Dict[str, int] = {}
        self._tasks: Set[asyncio.Task] = set()

    def spawn(self, session_id: str, job: Callable[[], Awaitable[None]]) -> asyncio.Task:
        # Counted now, not when the task first runs, so a chat is busy from the moment it has work.
        self._queued[session_id] = self._queued.get(session_id, 0) + 1
        lock = self._locks.setdefault(session_id, asyncio.Lock())
        task = asyncio.create_task(self._run(session_id, lock, job))
        self._tasks.add(task)
        task.add_done_callback(self._tasks.discard)
        return task

    async def _run(self, session_id: str, lock: asyncio.Lock, job: Callable[[], Awaitable[None]]) -> None:
        try:
            async with lock:
                await job()
        except Exception as exc:
            logger.error("Work after a turn failed for session %s: %s", session_id, exc, exc_info=True)
        finally:
            self._queued[session_id] -= 1
            if self._queued[session_id] == 0:
                del self._queued[session_id]
                del self._locks[session_id]

    def is_busy(self, session_id: str) -> bool:
        return session_id in self._queued

    @property
    def running(self) -> int:
        return len(self._tasks)

    @property
    def chats_held(self) -> int:
        return len(self._locks)
