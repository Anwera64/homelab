"""
How many PIN guesses a member gets, and how long they wait after that.

Six digits is a million combinations, which only holds up with a limit on guessing: five free
tries, then a wait that starts at 30 seconds and doubles with every further miss, up to 15 minutes.
"""

FREE_PIN_ATTEMPTS = 5
FIRST_LOCKOUT_SECONDS = 30
MAX_LOCKOUT_SECONDS = 15 * 60


def lockout_seconds_after(failed_attempts: int) -> int | None:
    """The wait a member's latest miss earns them, or None while they still have free tries."""
    if failed_attempts < FREE_PIN_ATTEMPTS:
        return None
    doublings = failed_attempts - FREE_PIN_ATTEMPTS
    return min(FIRST_LOCKOUT_SECONDS * 2**doublings, MAX_LOCKOUT_SECONDS)


def attempts_left_after(failed_attempts: int) -> int:
    return max(FREE_PIN_ATTEMPTS - failed_attempts, 0)
