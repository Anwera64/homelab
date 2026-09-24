from typing import List, Union
from pydantic import field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    ENVIRONMENT: str = "development"
    PROJECT_NAME: str = "Household Hub"
    API_V1_STR: str = "/api/v1"
    
    # Security & Tokens
    SECRET_KEY: str = "insecure-dev-secret-key-change-in-production-min-32chars"
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24 * 30  # 30 days default for homelab convenience
    
    # Database
    SQLITE_DB_PATH: str = "household_hub.db"
    
    # Business Logic Rules
    AGENT_DELETE_GRACE_DAYS: int = 7

    # Integration Settings
    SEARXNG_BASE_URL: str = "http://searxng:8080"
    SEARCH_TIMEOUT_SECONDS: float = 8.0
    SEARCH_CACHE_TTL_SECONDS: int = 900
    SEARXNG_CACHE_MAX_ENTRIES: int = 500
    CALENDAR_TIMEOUT_SECONDS: float = 10.0
    CALENDAR_ALLOW_AGENT_DELETE: bool = True
    PDF_PARSER_TIMEOUT_SECONDS: float = 30.0
    MAX_PDF_SIZE_BYTES: int = 25 * 1024 * 1024
    MAX_PDF_PAGES: int = 150

    # AI & LLM Inference Settings
    OLLAMA_BASE_URL: str = "http://ollama:11434"
    # Long enough to cover a cold load of the model into memory: 31 s for qwen3.8-rvn from the Docker volume, measured.
    OLLAMA_TIMEOUT_SECONDS: float = 120.0
    DEFAULT_LLM_MODEL: str = "qwen3.8-rvn"
    MAX_TOOL_CALL_ITERATIONS: int = 5
    MAX_CONTEXT_TOKENS: int = 8192
    MEMORY_REFLECTION_CONFIDENCE_THRESHOLD: float = 0.70
    MAX_GOSSIP_SUMMARY_LENGTH: int = 250
    
    # CORS
    CORS_ORIGINS: Union[List[str], str] = ["https://spicy-llama.duckdns.org"]

    @field_validator("CORS_ORIGINS", mode="before")
    @classmethod
    def assemble_cors_origins(cls, v: Union[str, List[str]]) -> List[str]:
        if isinstance(v, str) and not v.startswith("["):
            return [i.strip() for i in v.split(",") if i.strip()]
        return v

    @model_validator(mode="after")
    def validate_security(self) -> "Settings":
        insecure_keys = {
            "insecure-dev-secret-key-change-in-production-min-32chars",
            "changeme",
            "secret",
        }
        if self.ENVIRONMENT == "production":
            if self.SECRET_KEY in insecure_keys or len(self.SECRET_KEY) < 32:
                raise ValueError(
                    "Production deployment must specify a secure SECRET_KEY of at least 32 characters."
                )
        return self

    @property
    def database_url(self) -> str:
        if self.SQLITE_DB_PATH == ":memory:":
            return "sqlite+aiosqlite:///:memory:"
        return f"sqlite+aiosqlite:///{self.SQLITE_DB_PATH}"

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=True,
        extra="ignore"
    )


settings = Settings()
