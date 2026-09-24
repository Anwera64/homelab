"""
The backend runs as a compose service, so its defaults must be compose service names.

On the Windows host `searxng` did not resolve, and every web search failed with a DNS error.
"""
import pytest

from app.core.config import Settings
from app.data.connectors.ollama_llm_connector import OllamaLLMConnector
from app.data.connectors.searxng_search_connector import SearXNGSearchConnector


@pytest.fixture
def settings_without_overrides(monkeypatch: pytest.MonkeyPatch) -> Settings:
    monkeypatch.delenv("OLLAMA_BASE_URL", raising=False)
    monkeypatch.delenv("SEARXNG_BASE_URL", raising=False)
    return Settings(_env_file=None)


def test_settings_default_ollama_url_is_the_compose_service(settings_without_overrides: Settings):
    assert settings_without_overrides.OLLAMA_BASE_URL == "http://ollama:11434"


def test_settings_default_searxng_url_is_the_compose_service(settings_without_overrides: Settings):
    assert settings_without_overrides.SEARXNG_BASE_URL == "http://searxng:8080"


def test_ollama_connector_defaults_to_the_compose_service():
    assert OllamaLLMConnector().base_url == "http://ollama:11434"


def test_searxng_connector_defaults_to_the_compose_service():
    assert SearXNGSearchConnector().base_url == "http://searxng:8080"


def test_GIVEN_no_override_WHEN_settings_load_THEN_an_agent_gets_eight_model_calls_per_turn(
    settings_without_overrides: Settings,
):
    assert settings_without_overrides.MAX_TOOL_CALL_ITERATIONS == 8


def test_GIVEN_a_tool_budget_setting_WHEN_the_container_is_built_THEN_the_chat_turn_uses_it(
    monkeypatch: pytest.MonkeyPatch,
):
    from app.bootstrap import di
    from app.presentation.api import deps

    monkeypatch.setattr(di.settings, "MAX_TOOL_CALL_ITERATIONS", 3)

    container = di.get_container(session=None)

    assert container[deps.get_process_chat_turn_use_case].max_iterations == 3


def test_GIVEN_no_override_WHEN_settings_load_THEN_the_window_budgets_match_a_32k_model(
    settings_without_overrides: Settings,
):
    s = settings_without_overrides
    assert (s.LLM_CONTEXT_TOKENS, s.ANSWER_RESERVE_TOKENS) == (32768, 4096)
    assert (s.HISTORY_TOKENS, s.HISTORY_SUMMARY_TOKENS) == (6000, 1000)
    assert s.EMBEDDING_MODEL == "bge-m3-cpu"
    assert not hasattr(s, "MAX_CONTEXT_TOKENS")


def test_GIVEN_budget_settings_WHEN_the_container_is_built_THEN_the_turn_the_assembler_and_the_summary_use_them(
    monkeypatch: pytest.MonkeyPatch,
):
    from app.bootstrap import di
    from app.presentation.api import deps

    monkeypatch.setattr(di.settings, "LLM_CONTEXT_TOKENS", 20000)
    monkeypatch.setattr(di.settings, "ANSWER_RESERVE_TOKENS", 3000)
    monkeypatch.setattr(di.settings, "HISTORY_TOKENS", 5000)
    monkeypatch.setattr(di.settings, "HISTORY_SUMMARY_TOKENS", 900)

    container = di.get_container(session=None)

    turn = container[deps.get_process_chat_turn_use_case]
    assert (turn.context_window_tokens, turn.answer_reserve_tokens) == (20000, 3000)
    assert turn.source_index_factory is not None
    assert turn.tool_executor.page_reader is not None
    assert container[deps.get_assemble_agent_context_use_case].history_tokens == 5000
    summary = container[deps.get_summarize_history_use_case]
    assert (summary.history_tokens, summary.summary_tokens) == (5000, 900)


@pytest.mark.asyncio
async def test_GIVEN_a_finished_turn_WHEN_the_background_stream_ends_THEN_the_history_is_summarized_after_reflection(
    monkeypatch: pytest.MonkeyPatch,
):
    import asyncio
    from contextlib import asynccontextmanager

    from app.bootstrap import di
    from app.domain.entities.user import User
    from app.presentation.api import deps

    order = []

    class Turn:
        async def execute_stream(self, **kwargs):
            yield {"type": "done", "agent_id": "a1", "agent_name": "Assistant", "assistant_content": "Hi."}

    class Reflect:
        async def execute(self, **kwargs):
            order.append("reflect")

    class Summary:
        async def execute(self, session_id):
            order.append(("summary", session_id))

    runner = di.get_container(session=None)[deps.get_background_chat_stream_runner]

    @asynccontextmanager
    async def fake_session():
        yield None

    monkeypatch.setattr(di, "AsyncSessionLocal", fake_session)
    monkeypatch.setattr(di, "get_container", lambda session: {
        deps.get_process_chat_turn_use_case: Turn(),
        deps.get_reflect_turn_use_case: Reflect(),
        deps.get_summarize_history_use_case: Summary(),
    })

    queue: asyncio.Queue = asyncio.Queue()
    await runner("s1", User(id="u1", full_name="Alex"), "Hello", False, queue)

    assert order == ["reflect", ("summary", "s1")]


@pytest.mark.asyncio
async def test_GIVEN_a_non_streamed_turn_WHEN_reflection_runs_THEN_the_history_is_summarized_after_it(
    monkeypatch: pytest.MonkeyPatch,
):
    from contextlib import asynccontextmanager

    from app.bootstrap import di
    from app.presentation.api import deps

    order = []

    class Reflect:
        async def execute(self, **kwargs):
            order.append("reflect")

    class Summary:
        async def execute(self, session_id):
            order.append(("summary", session_id))

    runner = di.get_container(session=None)[deps.get_background_reflection_runner]

    @asynccontextmanager
    async def fake_session():
        yield None

    monkeypatch.setattr(di, "AsyncSessionLocal", fake_session)
    monkeypatch.setattr(di, "get_container", lambda session: {
        deps.get_reflect_turn_use_case: Reflect(),
        deps.get_summarize_history_use_case: Summary(),
    })

    await runner("s1", "u1", "Alex", "a1", "Assistant", "Hi", "Hello", False, False, False)

    assert order == ["reflect", ("summary", "s1")]
