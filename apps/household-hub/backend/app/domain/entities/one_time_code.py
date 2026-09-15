"""
The codes one member reads out to another across a room: an invite, a PIN reset.

Six characters from an alphabet without the pairs that sound or look alike (0/O, 1/I/L), good for
fifteen minutes and one use. Their strength is the expiry, the single use and the hub-wide guard on
guessing, not their length.
"""
import secrets
from datetime import timedelta
from typing import Callable

CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
CODE_LENGTH = 6
CODE_LIFETIME = timedelta(minutes=15)


def new_code(choose: Callable[[str], str] = secrets.choice) -> str:
    return "".join(choose(CODE_ALPHABET) for _ in range(CODE_LENGTH))


def normalise_code(code: str) -> str:
    """What was typed or pasted, as the hub stores it."""
    return code.strip().upper()
