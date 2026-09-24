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
