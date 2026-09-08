import asyncio
import os
import pytest
import pytest_asyncio
from typing import AsyncGenerator
import httpx
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from sqlalchemy.pool import StaticPool

# Set test environment before importing application modules
os.environ["ENVIRONMENT"] = "testing"
os.environ["SECRET_KEY"] = "test-secret-key-for-household-hub-testing-only-32chars"
os.environ["SQLITE_DB_PATH"] = ":memory:"

from app.core.database import Base
from app.bootstrap.di import get_db_session, setup_dependency_injection
from app.api.deps import get_db
from app.main import app

# Create in-memory test engine with StaticPool so all async connections share the same memory DB
TEST_DATABASE_URL = "sqlite+aiosqlite:///:memory:"

test_engine = create_async_engine(
    TEST_DATABASE_URL,
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,
)

TestingSessionLocal = async_sessionmaker(
    test_engine,
    class_=AsyncSession,
    expire_on_commit=False,
)


@pytest_asyncio.fixture(scope="function")
async def db_session() -> AsyncGenerator[AsyncSession, None]:
    """Provides a fresh, isolated database schema for each test."""
    async with test_engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    async with TestingSessionLocal() as session:
        from app.data.datasources.agent_data_source import SqliteAgentDataSource
        from app.data.repositories.agent_repository_impl import AgentRepositoryImpl
        from app.data.mappers.agent_data_mapper import AgentDataMapper
        from app.data.datasources.space_data_source import SqliteSpaceDataSource
        from app.data.repositories.space_repository_impl import SpaceRepositoryImpl
        from app.data.mappers.space_data_mapper import SpaceDataMapper
        from app.domain.use_cases.agents.seed_builtin_agents import SeedBuiltinAgentsUseCase
        from app.domain.use_cases.spaces.get_shared_space import GetSharedSpaceUseCase
        from app.data.persistence.unit_of_work import SqliteUnitOfWork

        uow = SqliteUnitOfWork(session)
        agent_repo = AgentRepositoryImpl(SqliteAgentDataSource(session), AgentDataMapper())
        space_repo = SpaceRepositoryImpl(SqliteSpaceDataSource(session), SpaceDataMapper())
        await SeedBuiltinAgentsUseCase(agent_repo, uow).execute()
        await GetSharedSpaceUseCase(space_repo, uow).execute()
        await session.commit()
        yield session

    async with test_engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)


@pytest_asyncio.fixture(scope="function")
async def client(db_session: AsyncSession) -> AsyncGenerator[httpx.AsyncClient, None]:
    """HTTP async test client with database dependency override."""
    async def override_get_db() -> AsyncGenerator[AsyncSession, None]:
        yield db_session

    app.dependency_overrides[get_db] = override_get_db
    app.dependency_overrides[get_db_session] = override_get_db

    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://testserver") as ac:
        yield ac

    app.dependency_overrides.pop(get_db, None)
    app.dependency_overrides.pop(get_db_session, None)
    setup_dependency_injection(app)
