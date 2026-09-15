"""
Signing people in for the HTTP tests.

The hub has no endpoint for adding a member until invites land, so `add_member` creates one
through the use case the invite flow will call, against the same in-memory database the app uses.
"""
import httpx

from app.data.datasources.space_data_source import SqliteSpaceDataSource
from app.data.datasources.user_data_source import SqliteUserDataSource
from app.data.mappers.space_data_mapper import SpaceDataMapper
from app.data.mappers.user_data_mapper import UserDataMapper
from app.data.persistence.unit_of_work import SqliteUnitOfWork
from app.data.repositories.space_repository_impl import SpaceRepositoryImpl
from app.data.repositories.user_repository_impl import UserRepositoryImpl
from app.data.security.bcrypt_hasher import BcryptPasswordHasher
from app.domain.use_cases.users.create_member import CreateMemberUseCase
from tests.conftest import TestingSessionLocal

ADMIN_PIN = "135790"
MEMBER_PIN = "246801"


def _user_repo(session) -> UserRepositoryImpl:
    return UserRepositoryImpl(SqliteUserDataSource(session), UserDataMapper())


async def register_admin(client: httpx.AsyncClient, full_name: str = "Admin", pin: str = ADMIN_PIN) -> tuple[str, str]:
    """First run. Returns (access_token, user_id)."""
    resp = await client.post("/api/v1/auth/register-initial", json={"full_name": full_name, "pin": pin})
    assert resp.status_code == 201, resp.text
    body = resp.json()
    return body["access_token"], body["user"]["id"]


async def add_member(full_name: str = "Member", pin: str = MEMBER_PIN, is_admin: bool = False) -> str:
    """Adds a member behind the API's back. Returns their id."""
    async with TestingSessionLocal() as session:
        use_case = CreateMemberUseCase(
            _user_repo(session),
            SpaceRepositoryImpl(SqliteSpaceDataSource(session), SpaceDataMapper()),
            BcryptPasswordHasher(),
            SqliteUnitOfWork(session),
        )
        member = await use_case.execute(full_name=full_name, pin=pin, is_admin=is_admin)
        return member.id


async def sign_in(client: httpx.AsyncClient, user_id: str, pin: str) -> str:
    resp = await client.post("/api/v1/auth/login", json={"user_id": user_id, "pin": pin})
    assert resp.status_code == 200, resp.text
    return resp.json()["access_token"]


async def add_signed_in_member(
    client: httpx.AsyncClient,
    full_name: str = "Member",
    pin: str = MEMBER_PIN,
    is_admin: bool = False,
) -> tuple[str, str]:
    """Returns (access_token, user_id)."""
    member_id = await add_member(full_name=full_name, pin=pin, is_admin=is_admin)
    return await sign_in(client, member_id, pin), member_id


async def bump_token_version(user_id: str) -> None:
    """What changing a PIN or removing a member does to the tokens already out there."""
    async with TestingSessionLocal() as session:
        repo = _user_repo(session)
        user = await repo.get_by_id(user_id)
        user.token_version += 1
        await repo.update(user)
        await session.commit()


async def deactivate(user_id: str) -> None:
    async with TestingSessionLocal() as session:
        repo = _user_repo(session)
        user = await repo.get_by_id(user_id)
        user.is_active = False
        await repo.update(user)
        await session.commit()
