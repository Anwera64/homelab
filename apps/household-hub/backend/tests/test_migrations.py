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
    """GIVEN a schema built by create_all WHEN the hub starts THEN it migrates with its rows intact."""
    from sqlalchemy import text
    from sqlalchemy.ext.asyncio import create_async_engine

    from app.core.database import _migrate

    with tempfile.TemporaryDirectory() as workspace:
        db_path = Path(workspace) / "legacy.db"

        # A hub that predates Alembic: every table, no alembic_version, and a row worth keeping.
        legacy = create_async_engine(f"sqlite+aiosqlite:///{db_path}")
        async with legacy.begin() as conn:
            await conn.run_sync(Base.metadata.create_all)
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
        finally:
            await migrated.dispose()
