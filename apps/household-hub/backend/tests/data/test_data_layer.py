import pytest
import pytest_asyncio
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from sqlalchemy.pool import StaticPool

from app.data.models.base import Base
from app.data.models.user_model import UserModel
from app.data.models.space_model import SpaceModel
from app.data.models.agent_model import AgentModel
from app.data.models.session_model import SessionModel, MessageModel
from app.data.models.memory_model import MemoryModel

from app.data.mappers.user_data_mapper import UserDataMapper
from app.data.mappers.space_data_mapper import SpaceDataMapper
from app.data.datasources.user_data_source import SqliteUserDataSource
from app.data.repositories.user_repository_impl import UserRepositoryImpl
from app.data.security.bcrypt_hasher import BcryptPasswordHasher
from app.data.security.jwt_token_service import JwtTokenService
from app.domain.entities.user import User


@pytest_asyncio.fixture
async def session():
    engine = create_async_engine(
        "sqlite+aiosqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    session_factory = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)
    async with session_factory() as sess:
        yield sess

    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)


@pytest.mark.asyncio
async def test_user_repository_and_mapper(session: AsyncSession):
    mapper = UserDataMapper()
    datasource = SqliteUserDataSource(session)
    repo = UserRepositoryImpl(datasource, mapper)

    user = User(
        id="user-123",
        username="anwera",
        email="anwera@homelab.local",
        full_name="Anwera",
        hashed_password="hash",
        is_admin=True,
    )

    created = await repo.create(user)
    await session.commit()

    assert created.id == "user-123"
    assert created.username == "anwera"

    fetched = await repo.get_by_id("user-123")
    assert fetched is not None
    assert fetched.username == "anwera"
    assert fetched.is_admin is True

    count = await repo.count()
    assert count == 1
    admins = await repo.count_admins()
    assert admins == 1


def test_security_hasher_and_token_service():
    hasher = BcryptPasswordHasher()
    hashed = hasher.hash("mysecretpassword")
    assert hasher.verify("mysecretpassword", hashed) is True
    assert hasher.verify("wrongpassword", hashed) is False

    token_svc = JwtTokenService(secret_key="a-very-long-secret-key-for-jwt-testing-32chars", algorithm="HS256")
    token = token_svc.create_access_token(subject="user-123", is_admin=True)
    decoded = token_svc.decode_token(token)
    assert decoded["sub"] == "user-123"
    assert decoded["is_admin"] is True
