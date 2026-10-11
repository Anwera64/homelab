"""Signing in, staying signed in and signing out, one device at a time (#94) — against fakes."""
import logging
from datetime import datetime, timedelta, timezone
from typing import Optional

import pytest

from app.domain.entities.device_session import DeviceSession
from app.domain.entities.user import User
from app.domain.exceptions import AuthenticationException
from app.domain.use_cases.auth.authenticate_token import AuthenticateTokenUseCase, SignedInMember
from app.domain.use_cases.auth.purge_expired_device_sessions import PurgeExpiredDeviceSessionsUseCase
from app.domain.use_cases.auth.refresh_token import RefreshTokenUseCase
from app.domain.use_cases.auth.sign_in_device import SignInDeviceUseCase
from app.domain.use_cases.auth.sign_out import SignOutUseCase

NOW = datetime(2026, 10, 11, 12, 0, tzinfo=timezone.utc)


class FakeUserRepository:
    def __init__(self, users=None):
        self.users = {u.id: u for u in (users or [])}

    async def get_by_id(self, user_id: str):
        return self.users.get(user_id)

    async def update(self, user: User) -> User:
        self.users[user.id] = user
        return user


class FakeDeviceSessionRepository:
    def __init__(self, device_sessions=None):
        self.rows = {d.id: d for d in (device_sessions or [])}

    async def create(self, device_session: DeviceSession) -> DeviceSession:
        self.rows[device_session.id] = device_session
        return device_session

    async def get_by_id(self, device_session_id: str) -> Optional[DeviceSession]:
        return self.rows.get(device_session_id)

    async def touch(self, device_session_id: str, seen_at: datetime) -> None:
        if device_session_id in self.rows:
            self.rows[device_session_id].last_seen_at = seen_at

    async def delete(self, device_session_id: str) -> None:
        self.rows.pop(device_session_id, None)

    async def delete_for_user(self, user_id: str, except_id: Optional[str] = None) -> None:
        self.rows = {
            key: row for key, row in self.rows.items() if row.user_id != user_id or key == except_id
        }

    async def delete_last_seen_before(self, cutoff: datetime) -> int:
        old = [key for key, row in self.rows.items() if row.last_seen_at < cutoff]
        for key in old:
            del self.rows[key]
        return len(old)


class FakeTokenService:
    """Decodes every token to the payload it was given, and makes tokens that spell out their claims."""

    def __init__(self, payload: Optional[dict] = None):
        self.payload = payload or {}

    def decode_token(self, token: str) -> dict:
        return self.payload

    def create_access_token(
        self, subject: str, is_admin: bool, token_version: int, session_id: Optional[str] = None
    ) -> str:
        return f"token:{subject}:{token_version}:{session_id}"


class FakeUnitOfWork:
    def __init__(self):
        self.commits = 0

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        return None

    async def commit(self):
        self.commits += 1

    async def rollback(self):
        return None


def _authenticator(user: User, devices: FakeDeviceSessionRepository, payload: dict) -> AuthenticateTokenUseCase:
    return AuthenticateTokenUseCase(FakeUserRepository([user]), FakeTokenService(payload), devices)


# ---- which tokens are accepted ----------------------------------------------


@pytest.mark.asyncio
async def test_GIVEN_a_device_still_signed_in_WHEN_its_token_is_shown_THEN_the_member_and_the_device_come_back():
    emma = User(full_name="Emma")
    phone = DeviceSession(user_id=emma.id)
    use_case = _authenticator(emma, FakeDeviceSessionRepository([phone]), {"sub": emma.id, "ver": 0, "sid": phone.id})

    signed_in = await use_case.authenticate("token")

    assert signed_in.user is emma
    assert signed_in.device_session_id == phone.id


@pytest.mark.asyncio
async def test_GIVEN_a_device_that_signed_out_WHEN_its_token_is_shown_THEN_it_is_refused():
    emma = User(full_name="Emma")
    use_case = _authenticator(emma, FakeDeviceSessionRepository(), {"sub": emma.id, "ver": 0, "sid": "signed-out"})

    with pytest.raises(AuthenticationException):
        await use_case.authenticate("token")


@pytest.mark.asyncio
async def test_GIVEN_a_token_naming_someone_elses_device_WHEN_it_is_shown_THEN_it_is_refused():
    emma = User(full_name="Emma")
    liams_phone = DeviceSession(user_id="liam")
    use_case = _authenticator(
        emma, FakeDeviceSessionRepository([liams_phone]), {"sub": emma.id, "ver": 0, "sid": liams_phone.id}
    )

    with pytest.raises(AuthenticationException):
        await use_case.authenticate("token")


@pytest.mark.asyncio
async def test_GIVEN_a_token_from_before_devices_were_tracked_WHEN_it_is_shown_THEN_it_is_accepted_with_no_device():
    emma = User(full_name="Emma")
    use_case = _authenticator(emma, FakeDeviceSessionRepository(), {"sub": emma.id, "ver": 0})

    signed_in = await use_case.authenticate("token")

    assert signed_in.user is emma
    assert signed_in.device_session_id is None


@pytest.mark.asyncio
async def test_a_token_of_the_members_current_version_is_accepted():
    emma = User(full_name="Emma", token_version=2)
    use_case = _authenticator(emma, FakeDeviceSessionRepository(), {"sub": emma.id, "ver": 2})

    assert await use_case.execute("token") is emma


@pytest.mark.asyncio
async def test_a_token_from_an_older_version_is_refused():
    emma = User(full_name="Emma", token_version=2)
    use_case = _authenticator(emma, FakeDeviceSessionRepository(), {"sub": emma.id, "ver": 1})

    with pytest.raises(AuthenticationException):
        await use_case.execute("token")


@pytest.mark.asyncio
async def test_a_token_without_a_version_is_refused():
    emma = User(full_name="Emma")
    use_case = _authenticator(emma, FakeDeviceSessionRepository(), {"sub": emma.id})

    with pytest.raises(AuthenticationException):
        await use_case.execute("token")


# ---- refreshing ---------------------------------------------------------------


def _refresher(devices: FakeDeviceSessionRepository) -> RefreshTokenUseCase:
    tokens, uow = FakeTokenService(), FakeUnitOfWork()
    return RefreshTokenUseCase(
        tokens, devices, SignInDeviceUseCase(devices, tokens, uow, clock=lambda: NOW), uow, clock=lambda: NOW
    )


@pytest.mark.asyncio
async def test_GIVEN_a_signed_in_device_WHEN_its_token_is_refreshed_THEN_the_new_token_names_the_same_device():
    emma = User(full_name="Emma")
    phone = DeviceSession(user_id=emma.id, last_seen_at=NOW - timedelta(days=3))
    devices = FakeDeviceSessionRepository([phone])

    answer = await _refresher(devices).execute(SignedInMember(user=emma, device_session_id=phone.id))

    assert answer["access_token"] == f"token:{emma.id}:0:{phone.id}"
    assert answer["user"] is emma
    assert devices.rows[phone.id].last_seen_at == NOW
    assert list(devices.rows) == [phone.id]


@pytest.mark.asyncio
async def test_GIVEN_a_token_naming_no_device_WHEN_it_is_refreshed_THEN_the_device_gets_a_session_and_a_token_naming_it():
    emma = User(full_name="Emma")
    devices = FakeDeviceSessionRepository()

    answer = await _refresher(devices).execute(SignedInMember(user=emma))

    (opened,) = devices.rows.values()
    assert opened.user_id == emma.id
    assert answer["access_token"] == f"token:{emma.id}:0:{opened.id}"


# ---- signing out --------------------------------------------------------------

SIGN_OUT_LOG = "app.domain.use_cases.auth.sign_out"


@pytest.mark.asyncio
async def test_GIVEN_a_member_on_two_devices_WHEN_one_signs_out_THEN_only_that_device_is_forgotten(caplog):
    emma = User(full_name="Emma")
    phone, tablet = DeviceSession(user_id=emma.id), DeviceSession(user_id=emma.id)
    devices = FakeDeviceSessionRepository([phone, tablet])
    uow = FakeUnitOfWork()

    with caplog.at_level(logging.INFO, logger=SIGN_OUT_LOG):
        await SignOutUseCase(devices, uow).execute(SignedInMember(user=emma, device_session_id=phone.id))

    assert list(devices.rows) == [tablet.id]
    assert uow.commits == 1
    assert [record.getMessage() for record in caplog.records] == [
        f"Signed out | member={emma.id} device={phone.id}"
    ]


@pytest.mark.asyncio
async def test_GIVEN_a_token_naming_no_device_WHEN_it_signs_out_THEN_it_is_logged_and_nothing_is_forgotten(caplog):
    emma = User(full_name="Emma")
    tablet = DeviceSession(user_id=emma.id)
    devices = FakeDeviceSessionRepository([tablet])

    with caplog.at_level(logging.INFO, logger=SIGN_OUT_LOG):
        await SignOutUseCase(devices, FakeUnitOfWork()).execute(SignedInMember(user=emma))

    assert list(devices.rows) == [tablet.id]
    assert [record.getMessage() for record in caplog.records] == [f"Signed out | member={emma.id} device=none"]


# ---- devices nobody came back to ----------------------------------------------


@pytest.mark.asyncio
async def test_GIVEN_a_device_whose_last_token_has_expired_WHEN_the_purge_runs_THEN_it_is_forgotten_and_a_live_one_is_kept():
    lifetime = timedelta(days=30)
    expired = DeviceSession(user_id="emma", last_seen_at=NOW - lifetime - timedelta(minutes=1))
    live = DeviceSession(user_id="emma", last_seen_at=NOW - lifetime + timedelta(minutes=1))
    devices = FakeDeviceSessionRepository([expired, live])
    uow = FakeUnitOfWork()

    removed = await PurgeExpiredDeviceSessionsUseCase(devices, uow, lifetime, clock=lambda: NOW).execute()

    assert removed == 1
    assert list(devices.rows) == [live.id]
    assert uow.commits == 1
