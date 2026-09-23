"""
An agent never names a model. It either follows the household default or points at one model row,
and this is the one place that turns that into the name the inference server knows.
"""

import pytest

from app.domain.entities.agent import AgentPersonality
from app.domain.entities.llm_model import LLMModel
from app.domain.exceptions import ModelNotConfiguredException
from app.domain.use_cases.models.resolve_agent_model import ResolveAgentModelUseCase


class FakeLLMModelRepository:
    def __init__(self, models=None):
        self.models = {m.id: m for m in (models or [])}

    async def get_default(self):
        return next((m for m in self.models.values() if m.is_default), None)

    async def get_by_id(self, model_id: str):
        return self.models.get(model_id)


HOUSE_DEFAULT = LLMModel(id="m-default", provider_model="house-model", display_name="House", is_default=True)
PINNED = LLMModel(id="m-pinned", provider_model="pinned-model", display_name="Pinned", is_default=False)


@pytest.mark.asyncio
async def test_an_agent_that_follows_the_default_gets_the_household_default():
    """GIVEN an agent with no model of its own WHEN its model is resolved THEN it is the household default."""
    resolver = ResolveAgentModelUseCase(FakeLLMModelRepository([HOUSE_DEFAULT, PINNED]))

    assert await resolver.for_agent(AgentPersonality(id="a1")) == "house-model"


@pytest.mark.asyncio
async def test_an_agent_pinned_to_a_model_gets_that_model():
    """GIVEN an agent pinned to a model WHEN its model is resolved THEN it is the pinned one, not the default."""
    resolver = ResolveAgentModelUseCase(FakeLLMModelRepository([HOUSE_DEFAULT, PINNED]))

    assert await resolver.for_agent(AgentPersonality(id="a1", llm_model_id="m-pinned")) == "pinned-model"


@pytest.mark.asyncio
async def test_the_default_on_its_own_is_the_household_default():
    """GIVEN a household default WHEN the default is asked for THEN its provider model comes back."""
    resolver = ResolveAgentModelUseCase(FakeLLMModelRepository([HOUSE_DEFAULT]))

    assert await resolver.default() == "house-model"


@pytest.mark.asyncio
async def test_no_default_model_says_so():
    """GIVEN no household default WHEN an agent following it is resolved THEN it fails with a clear reason."""
    resolver = ResolveAgentModelUseCase(FakeLLMModelRepository([PINNED]))

    with pytest.raises(ModelNotConfiguredException):
        await resolver.for_agent(AgentPersonality(id="a1"))


@pytest.mark.asyncio
async def test_a_pin_to_a_model_that_is_gone_says_so():
    """GIVEN an agent pinned to a model that no longer exists WHEN resolved THEN it fails rather than guessing."""
    resolver = ResolveAgentModelUseCase(FakeLLMModelRepository([HOUSE_DEFAULT]))

    with pytest.raises(ModelNotConfiguredException):
        await resolver.for_agent(AgentPersonality(id="a1", llm_model_id="m-missing"))
