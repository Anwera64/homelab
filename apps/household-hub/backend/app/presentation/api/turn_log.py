import asyncio
import time
import uuid
from typing import AsyncIterator, Callable, Coroutine, Dict, List, Optional, Set, Tuple


class TurnLog:
    """
    Everything one turn has said so far, numbered from 1, for as many listeners as come asking.

    The worker writes here the way it used to write to its queue — `put(event)`, then `put(None)`
    to end — so the runner never learns the difference. What changed is the reading end: a queue
    hands each event to exactly one reader and forgets it, so a phone whose socket died took the
    rest of the answer down with it. A log keeps them, and a phone that comes back asks for
    everything after the last number it saw.
    """

    def __init__(self, clock: Callable[[], float] = time.monotonic) -> None:
        self._clock = clock
        self.turn_id = uuid.uuid4().hex
        self.events: List[dict] = []
        self.closed = False
        self.closed_at: Optional[float] = None
        self._changed = asyncio.Condition()

    async def put(self, item: Optional[dict]) -> None:
        async with self._changed:
            if item is None:
                self.closed = True
                self.closed_at = self._clock()
            else:
                self.events.append(item)
            self._changed.notify_all()

    async def follow(self, after: int = 0) -> AsyncIterator[Tuple[int, dict]]:
        """Every event numbered above [after], live, until the turn ends."""
        seen = max(after, 0)
        while True:
            async with self._changed:
                await self._changed.wait_for(lambda: len(self.events) > seen or self.closed)
                pending = self.events[seen:]
                ended = self.closed
            for event in pending:
                seen += 1
                yield seen, event
            if ended and seen >= len(self.events):
                return


class TurnLogRegistry:
    """
    The latest turn of each conversation, kept a while after it ends so a late phone can finish it.

    A running turn is never dropped. A finished one is kept for [retention_s] — long enough for a
    phone to be unlocked and ask — and after that the saved message is the answer, which the phone
    reads the ordinary way. Per-process, like `SessionLockRegistry`.

    It also holds the workers. The event loop keeps only weak references to tasks, so a turn started
    with a bare `create_task` and not stored anywhere can be collected halfway through an answer.
    """

    RETENTION_S = 300

    def __init__(
        self,
        clock: Callable[[], float] = time.monotonic,
        retention_s: float = RETENTION_S,
    ) -> None:
        self._clock = clock
        self._retention_s = retention_s
        self._logs: Dict[str, TurnLog] = {}
        self._workers: Set[asyncio.Task] = set()

    def start(self, session_id: str) -> TurnLog:
        log = TurnLog(clock=self._clock)
        self._logs[session_id] = log
        return log

    def get(self, session_id: str) -> Optional[TurnLog]:
        log = self._logs.get(session_id)
        if log is None:
            return None
        if log.closed and log.closed_at is not None and self._clock() - log.closed_at > self._retention_s:
            del self._logs[session_id]
            return None
        return log

    def spawn(self, coro: Coroutine) -> asyncio.Task:
        task = asyncio.create_task(coro)
        self._workers.add(task)
        task.add_done_callback(self._workers.discard)
        return task

    @property
    def running_workers(self) -> int:
        return len(self._workers)
