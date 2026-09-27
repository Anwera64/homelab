from typing import Protocol


class ISignInStateService(Protocol):
    """
    The `state` a calendar sign-in carries through Google and back. It ties the callback, which
    arrives from a browser with no member token, to the member who started the sign-in.
    """

    def issue(self, user_id: str) -> str:
        ...

    def verify(self, state: str) -> str:
        """The member's id. Raises CalendarSignInExpiredException for anything the hub did not issue lately."""
        ...
