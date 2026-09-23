"""
Switching the household to another model is one setting and a restart: on startup the default row
is made to name whatever the setting says, and every agent following the default follows it too.
"""

import pytest

from app.domain.entities.agent import AgentPersonality
from app.domain.entities.llm_model import LLMModel
from app.domain.use_cases.models.resolve_agent_model import ResolveAgentModelUseCase
from app.domain.use_cases.models.sync_default_model import SyncDefaultModelUseCase


class FakeLLMModelRepository:
    def __init__(self, default=None):
        self.default = default
        self.writes = []

    async def get_default(self):
        return self.default

    async def get_by_id(self, model_id: str):
        return self.default if self.default and self.default.id == model_id else None

    async def set_default_provider_model(self, provider_model: str):
        self.writes.append(provider_model)
        if self.default is None:
            self.default = LLMModel(id="m1", provider_model=provider_model, is_default=True)
        else:
            self.default.provider_model = provider_model
        return self.default


class FakeUnitOfWork:
    def __init__(self):
        self.commits = 0

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        pass

    async def commit(self):
        self.commits += 1


@pytest.mark.asyncio
async def test_a_changed_setting_repoints_the_default_and_its_followers():
    """GIVEN a default on one model WHEN startup syncs to another THEN agents following the default use the new one."""
    repo = FakeLLMModelRepository(LLMModel(id="m1", provider_model="old-model", is_default=True))
    uow = FakeUnitOfWork()

    await SyncDefaultModelUseCase(repo, uow).execute("new-model")

    assert await ResolveAgentModelUseCase(repo).for_agent(AgentPersonality(id="a1")) == "new-model"
    assert uow.commits == 1


@pytest.mark.asyncio
async def test_a_fresh_household_gets_its_default_from_the_setting():
    """GIVEN no models yet WHEN startup syncs THEN the default exists and names the setting's model."""
    repo = FakeLLMModelRepository()

    await SyncDefaultModelUseCase(repo, FakeUnitOfWork()).execute("house-model")

    assert (await repo.get_default()).provider_model == "house-model"


@pytest.mark.asyncio
async def test_an_unchanged_setting_writes_nothing():
    """GIVEN the default already names the setting's model WHEN startup syncs THEN nothing is written."""
    repo = FakeLLMModelRepository(LLMModel(id="m1", provider_model="house-model", is_default=True))
    uow = FakeUnitOfWork()

    await SyncDefaultModelUseCase(repo, uow).execute("house-model")

    assert repo.writes == []
    assert uow.commits == 0
