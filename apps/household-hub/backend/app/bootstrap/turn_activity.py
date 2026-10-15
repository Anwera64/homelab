import logging
from typing import Callable, List, Optional

from app.domain.repositories.turn_activity import ITurnActivity

logger = logging.getLogger(__name__)


class HubTurnActivity(ITurnActivity):
    """
    One per hub, fed by `SessionLockRegistry`, which brackets every turn: plain, streamed,
    regenerated or resumed after a card. Work a turn leaves behind (the history summary) counts as
    the turn still running, so reflection never takes the model slot in front of it. Per-process,
    like the registry; the hub is a single process.
    """

    def __init__(self, after_turn_busy: Optional[Callable[[], bool]] = None) -> None:
        self._running = 0
        self._listeners: List[Callable[[], None]] = []
        self._after_turn_busy = after_turn_busy or (lambda: False)

    def turn_started(self) -> None:
        self._running += 1
        for listener in list(self._listeners):
            try:
                listener()
            except Exception as exc:
                logger.error("A turn-start listener failed: %s", exc, exc_info=True)

    def turn_ended(self) -> None:
        self._running = max(0, self._running - 1)

    def any_running(self) -> bool:
        return self._running > 0 or self._after_turn_busy()

    def on_turn_start(self, listener: Callable[[], None]) -> None:
        self._listeners.append(listener)
