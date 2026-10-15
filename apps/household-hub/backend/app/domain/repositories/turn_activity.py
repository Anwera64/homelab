from typing import Callable, Protocol


class ITurnActivity(Protocol):
    """
    Whether a turn is running anywhere on the hub (memory spec §2.2: a turn always wins). The chat
    locks report each turn; background work asks before it starts and listens for a turn starting.
    """

    def turn_started(self) -> None:
        ...

    def turn_ended(self) -> None:
        ...

    def any_running(self) -> bool:
        ...

    def on_turn_start(self, listener: Callable[[], None]) -> None:
        """Calls [listener] each time a turn starts, at once and on the event loop."""
        ...
