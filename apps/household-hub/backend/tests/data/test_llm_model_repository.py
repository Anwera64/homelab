import pytest
import pytest_asyncio
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from sqlalchemy.pool import StaticPool

from app.data.models.base import Base
import app.data.models.user_model  # noqa: F401  (agent_personalities references users)
import app.data.models.session_model  # noqa: F401
from app.data.datasources.agent_data_source import SqliteAgentDataSource
from app.data.datasources.llm_model_data_source import SqliteLLMModelDataSource
from app.data.mappers.agent_data_mapper import AgentDataMapper
from app.data.mappers.llm_model_data_mapper import LLMModelDataMapper
from app.data.repositories.agent_repository_impl import AgentRepositoryImpl
from app.data.repositories.llm_model_repository_impl import LLMModelRepositoryImpl
from app.domain.entities.agent import AgentPersonality


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

    await engine.dispose()


def _repo(session):
    return LLMModelRepositoryImpl(SqliteLLMModelDataSource(session), LLMModelDataMapper())


@pytest.mark.asyncio
async def test_a_fresh_household_has_no_default_model(session):
    """GIVEN no models WHEN the default is asked for THEN there is none."""
    assert await _repo(session).get_default() is None


@pytest.mark.asyncio
async def test_setting_the_default_creates_it_when_there_is_none(session):
    """GIVEN no models WHEN the default is set THEN one default row holds that provider model."""
    repo = _repo(session)

    created = await repo.set_default_provider_model("house-model")
    await session.commit()

    default = await repo.get_default()
    assert default.provider_model == "house-model"
    assert default.is_default is True
    assert default.id == created.id


@pytest.mark.asyncio
async def test_setting_the_default_again_repoints_the_same_row(session):
    """GIVEN a default WHEN it is set to another model THEN the same row now names the new model."""
    repo = _repo(session)
    first = await repo.set_default_provider_model("old-model")
    await session.commit()

    second = await repo.set_default_provider_model("new-model")
    await session.commit()

    assert second.id == first.id
    assert (await repo.get_default()).provider_model == "new-model"
    assert (await repo.get_by_id(first.id)).provider_model == "new-model"


@pytest.mark.asyncio
async def test_an_agent_keeps_its_model_pin_through_the_database(session):
    """GIVEN an agent pinned to a model WHEN it is saved and read back THEN the pin survives; unpinned stays None."""
    model = await _repo(session).set_default_provider_model("house-model")
    agents = AgentRepositoryImpl(SqliteAgentDataSource(session), AgentDataMapper())

    await agents.create(AgentPersonality(id="pinned", slug="pinned", name="P", system_prompt="p", llm_model_id=model.id))
    await agents.create(AgentPersonality(id="follower", slug="follower", name="F", system_prompt="f"))
    await session.commit()

    assert (await agents.get_by_id("pinned")).llm_model_id == model.id
    assert (await agents.get_by_id("follower")).llm_model_id is None
