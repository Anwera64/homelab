from datetime import datetime, timedelta, timezone

from app.domain.entities.invite import Invite
from app.domain.entities.one_time_code import CODE_ALPHABET, new_code, normalise_code


def test_codes_use_only_characters_that_read_aloud_cleanly():
    assert not set("0O1IL") & set(CODE_ALPHABET)

    for code in (new_code() for _ in range(200)):
        assert len(code) == 6
        assert set(code) <= set(CODE_ALPHABET)


def test_a_typed_code_is_trimmed_and_upper_cased():
    assert normalise_code("  k7m2qp\n") == "K7M2QP"


def test_an_invite_is_usable_until_it_expires_or_is_used():
    now = datetime(2026, 9, 15, 12, 0, tzinfo=timezone.utc)
    invite = Invite(code="K7M2QP", invited_name="Liam", inviter_id="emma", expires_at=now + timedelta(minutes=15))

    assert invite.is_usable_at(now)
    assert not invite.is_usable_at(now + timedelta(minutes=15))
    invite.used_at = now
    assert not invite.is_usable_at(now)
