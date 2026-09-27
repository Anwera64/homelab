"""
The migration history and the ORM models must say the same thing.

`Base.metadata.create_all` builds the schema for tests, and Alembic builds it for the running hub.
Two sources for one schema drift the moment someone adds a column and forgets the revision — and
the symptom is a hub that starts fine and fails on the first query. This compares them directly:
migrate an empty database to head, then ask Alembic what it would still autogenerate. Anything at
all means the two disagree.
"""

import os
import tempfile
from pathlib import Path

import pytest
from alembic import command
from alembic.autogenerate import compare_metadata
from alembic.config import Config
from alembic.runtime.migration import MigrationContext
from sqlalchemy import create_engine

from app.core.database import Base

BACKEND_ROOT = Path(__file__).resolve().parent.parent


def _alembic_config(database_url: str) -> Config:
    config = Config(str(BACKEND_ROOT / "alembic.ini"))
    config.set_main_option("script_location", str(BACKEND_ROOT / "alembic"))
    config.set_main_option("sqlalchemy.url", database_url)
    return config


def test_migrations_match_the_models():
    """GIVEN an empty database WHEN it is migrated to head THEN nothing is left to autogenerate."""
    with tempfile.TemporaryDirectory() as workspace:
        db_path = Path(workspace) / "drift-check.db"
        database_url = f"sqlite:///{db_path}"

        command.upgrade(_alembic_config(database_url), "head")

        engine = create_engine(database_url)
        try:
            with engine.connect() as connection:
                context = MigrationContext.configure(
                    connection,
                    opts={"compare_type": True, "target_metadata": Base.metadata},
                )
                difference = compare_metadata(context, Base.metadata)
        finally:
            engine.dispose()

    assert difference == [], (
        "The models and the migrations disagree. Generate a revision for the difference below:\n"
        f"{difference}"
    )


@pytest.mark.asyncio
async def test_a_database_from_before_alembic_is_adopted_not_wiped():
    """GIVEN a pre-Alembic schema WHEN the hub starts THEN it migrates with its rows intact.

    This also proves the upgrade *runs* rather than being stamped over: the index added after the
    baseline has to exist afterwards, on a database that never had it.
    """
    from sqlalchemy import text
    from sqlalchemy.ext.asyncio import create_async_engine

    from app.core.database import _migrate

    with tempfile.TemporaryDirectory() as workspace:
        db_path = Path(workspace) / "legacy.db"
        database_url = f"sqlite:///{db_path}"

        # A hub that predates Alembic has the schema as it stood when Alembic arrived — which is
        # what the baseline revision describes — and no alembic_version table. Building it from
        # today's metadata instead would give it revisions it never ran, and the test would be
        # asserting against a database that cannot exist.
        command.upgrade(_alembic_config(database_url), "efd5e0befdb0")

        legacy = create_async_engine(f"sqlite+aiosqlite:///{db_path}")
        async with legacy.begin() as conn:
            await conn.execute(text("DROP TABLE alembic_version"))
            await conn.execute(
                text(
                    "INSERT INTO system_settings (key, value, created_at, updated_at) "
                    "VALUES ('household_name', 'HyggeHub', '2026-01-01', '2026-01-01')"
                )
            )
        await legacy.dispose()

        migrated = create_async_engine(f"sqlite+aiosqlite:///{db_path}")
        try:
            async with migrated.begin() as conn:
                await conn.run_sync(_migrate)

            async with migrated.connect() as conn:
                stamped = await conn.execute(text("SELECT version_num FROM alembic_version"))
                assert stamped.scalar() is not None

                survived = await conn.execute(
                    text("SELECT value FROM system_settings WHERE key = 'household_name'")
                )
                assert survived.scalar() == "HyggeHub"

                applied = await conn.execute(
                    text(
                        "SELECT name FROM sqlite_master WHERE type = 'index' "
                        "AND name = 'ix_chat_messages_session_created'"
                    )
                )
                assert applied.scalar() == "ix_chat_messages_session_created"
        finally:
            await migrated.dispose()


def test_migrating_does_not_silence_the_application_log():
    """
    GIVEN loggers the app has already set up WHEN it migrates THEN they still work.

    `fileConfig` disables every existing logger by default, and the hub migrates inside its own
    startup — so running it took uvicorn's loggers down with it, `uvicorn.access` included. The hub
    went on serving perfectly and said nothing about it, which is the worst of both.
    """
    import logging

    already_running = logging.getLogger("uvicorn.access")
    already_running.disabled = False

    with tempfile.TemporaryDirectory() as workspace:
        db_path = Path(workspace) / "logging-check.db"
        command.upgrade(_alembic_config(f"sqlite:///{db_path}"), "head")

    assert not already_running.disabled, (
        "Migrating disabled a logger the application had already configured; env.py must not take "
        "the running process's logging with it."
    )


_BEFORE_MODEL_TABLE = "180c460dc76b"


def _insert_agent(connection, agent_id: str, model_alias: str, is_builtin: bool) -> None:
    from sqlalchemy import text

    connection.execute(
        text(
            "INSERT INTO agent_personalities (id, slug, name, description, avatar, system_prompt, "
            "model_alias, temperature, top_p, tool_permissions, is_builtin, is_active, created_at, updated_at) "
            "VALUES (:id, :id, :id, '', '🤖', 'prompt', :alias, 0.5, 0.9, '[]', :builtin, 1, "
            "'2026-01-01', '2026-01-01')"
        ),
        {"id": agent_id, "alias": model_alias, "builtin": is_builtin},
    )


def test_agents_stop_naming_models_and_follow_the_household_default():
    """
    GIVEN agents that each name a model WHEN migrated THEN the built-ins' model becomes the household
    default they follow, any other model becomes its own row they point at, and the name is gone
    from the agents. Downgrading puts every name back.
    """
    from sqlalchemy import text

    with tempfile.TemporaryDirectory() as workspace:
        db_path = Path(workspace) / "models.db"
        database_url = f"sqlite:///{db_path}"
        config = _alembic_config(database_url)
        command.upgrade(config, _BEFORE_MODEL_TABLE)

        engine = create_engine(database_url)
        try:
            with engine.begin() as conn:
                _insert_agent(conn, "researcher", "qwen3:14b", True)
                _insert_agent(conn, "assistant", "qwen3:14b", True)
                _insert_agent(conn, "custom", "llama3:8b", False)

            command.upgrade(config, "head")

            with engine.connect() as conn:
                models = {
                    row.provider_model: row
                    for row in conn.execute(text("SELECT id, provider_model, is_default FROM llm_models"))
                }
                assert set(models) == {"qwen3:14b", "llama3:8b"}
                assert models["qwen3:14b"].is_default
                assert not models["llama3:8b"].is_default

                pins = dict(conn.execute(text("SELECT id, llm_model_id FROM agent_personalities")).all())
                assert pins == {"researcher": None, "assistant": None, "custom": models["llama3:8b"].id}

                columns = {row[1] for row in conn.execute(text("PRAGMA table_info(agent_personalities)"))}
                assert "model_alias" not in columns

            command.downgrade(config, _BEFORE_MODEL_TABLE)

            with engine.connect() as conn:
                names = dict(conn.execute(text("SELECT id, model_alias FROM agent_personalities")).all())
                assert names == {"researcher": "qwen3:14b", "assistant": "qwen3:14b", "custom": "llama3:8b"}
        finally:
            engine.dispose()


_BEFORE_RESEARCHER_WARMS_UP = "226eae49047c"


def _agents_at(database_url: str, rows) -> dict:
    """Migrates a fresh database to just before the change, inserts agents, and upgrades to head."""
    from sqlalchemy import text

    config = _alembic_config(database_url)
    command.upgrade(config, _BEFORE_RESEARCHER_WARMS_UP)
    engine = create_engine(database_url)
    try:
        with engine.begin() as conn:
            for slug, temperature, is_builtin in rows:
                conn.execute(
                    text(
                        "INSERT INTO agent_personalities (id, slug, name, description, avatar, system_prompt, "
                        "temperature, top_p, tool_permissions, is_builtin, is_active, created_at, updated_at) "
                        "VALUES (:slug, :slug, :slug, '', '🤖', 'prompt', :t, 0.85, '[]', :b, 1, "
                        "'2026-01-01', '2026-01-01')"
                    ),
                    {"slug": slug, "t": temperature, "b": is_builtin},
                )
        command.upgrade(config, "head")
        with engine.connect() as conn:
            upgraded = dict(conn.execute(text("SELECT slug, temperature FROM agent_personalities")).all())
        command.downgrade(config, _BEFORE_RESEARCHER_WARMS_UP)
        with engine.connect() as conn:
            downgraded = dict(conn.execute(text("SELECT slug, temperature FROM agent_personalities")).all())
        return {"upgraded": upgraded, "downgraded": downgraded}
    finally:
        engine.dispose()


def test_GIVEN_the_builtin_researcher_at_0_3_WHEN_migrated_THEN_it_runs_at_0_6_and_back_on_downgrade():
    """
    The Researcher ran a thinking model at 0.3, far below its own 1.0, and sent garbled tool names.
    Only the untouched built-in moves: a temperature someone chose stays theirs.
    """
    with tempfile.TemporaryDirectory() as workspace:
        result = _agents_at(
            f"sqlite:///{Path(workspace) / 'warm.db'}",
            [("researcher", 0.3, True), ("custom", 0.3, False)],
        )

    assert result["upgraded"] == {"researcher": 0.6, "custom": 0.3}
    assert result["downgraded"] == {"researcher": 0.3, "custom": 0.3}


def test_GIVEN_a_researcher_someone_retuned_WHEN_migrated_THEN_its_temperature_is_left_alone():
    with tempfile.TemporaryDirectory() as workspace:
        result = _agents_at(f"sqlite:///{Path(workspace) / 'edited.db'}", [("researcher", 0.45, True)])

    assert result["upgraded"] == {"researcher": 0.45}
    assert result["downgraded"] == {"researcher": 0.45}


_BEFORE_RESEARCHER_KNOWS_SCIENCE_SEARCH = "7c3e91a0b5d2"
_RESEARCHER_PROMPT_BEFORE_SCIENCE_SEARCH = (
    "You are the Household Academic & Document Researcher. You specialize in deep academic analysis, "
    "synthesizing complex documents, reading architectural papers, and extracting exact citations. "
    "You maintain an objective, thorough, and rigorous research tone."
)


def _prompts_at(database_url: str, rows) -> dict:
    """Migrates to just before the prompt change, inserts agents, upgrades to head and back down."""
    from sqlalchemy import text

    config = _alembic_config(database_url)
    command.upgrade(config, _BEFORE_RESEARCHER_KNOWS_SCIENCE_SEARCH)
    engine = create_engine(database_url)
    try:
        with engine.begin() as conn:
            for slug, prompt, is_builtin in rows:
                conn.execute(
                    text(
                        "INSERT INTO agent_personalities (id, slug, name, description, avatar, system_prompt, "
                        "temperature, top_p, tool_permissions, is_builtin, is_active, created_at, updated_at) "
                        "VALUES (:slug, :slug, :slug, '', '🤖', :prompt, 0.6, 0.85, '[]', :b, 1, "
                        "'2026-01-01', '2026-01-01')"
                    ),
                    {"slug": slug, "prompt": prompt, "b": is_builtin},
                )
        command.upgrade(config, "head")
        with engine.connect() as conn:
            upgraded = dict(conn.execute(text("SELECT slug, system_prompt FROM agent_personalities")).all())
        command.downgrade(config, _BEFORE_RESEARCHER_KNOWS_SCIENCE_SEARCH)
        with engine.connect() as conn:
            downgraded = dict(conn.execute(text("SELECT slug, system_prompt FROM agent_personalities")).all())
        return {"upgraded": upgraded, "downgraded": downgraded}
    finally:
        engine.dispose()


def test_GIVEN_the_untouched_builtin_researcher_WHEN_migrated_THEN_it_gets_the_seeded_prompt_and_back_on_downgrade():
    """An existing hub's Researcher learns when to search science, the same words a new hub seeds (#38)."""
    from app.domain.use_cases.agents.seed_builtin_agents import BUILTIN_AGENTS

    seeded = next(a for a in BUILTIN_AGENTS if a["slug"] == "researcher")["system_prompt"]
    old = _RESEARCHER_PROMPT_BEFORE_SCIENCE_SEARCH
    with tempfile.TemporaryDirectory() as workspace:
        result = _prompts_at(
            f"sqlite:///{Path(workspace) / 'science.db'}",
            [("researcher", old, True), ("custom", old, False)],
        )

    assert result["upgraded"] == {"researcher": seeded, "custom": old}
    assert result["downgraded"] == {"researcher": old, "custom": old}


def test_GIVEN_a_researcher_prompt_someone_rewrote_WHEN_migrated_THEN_it_is_left_alone():
    with tempfile.TemporaryDirectory() as workspace:
        result = _prompts_at(
            f"sqlite:///{Path(workspace) / 'rewritten.db'}",
            [("researcher", "You only read Spanish sources.", True)],
        )

    assert result["upgraded"] == {"researcher": "You only read Spanish sources."}
    assert result["downgraded"] == {"researcher": "You only read Spanish sources."}
