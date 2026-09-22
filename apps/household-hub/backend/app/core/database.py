from pathlib import Path

from sqlalchemy import event, inspect
from sqlalchemy.engine import Engine
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from sqlalchemy.orm import declarative_base

from app.core.config import settings

# Engine configuration
engine_kwargs = {"echo": False}
if settings.SQLITE_DB_PATH == ":memory:":
    from sqlalchemy.pool import StaticPool
    engine_kwargs["poolclass"] = StaticPool
    engine_kwargs["connect_args"] = {"check_same_thread": False}
else:
    engine_kwargs["connect_args"] = {"check_same_thread": False}

engine = create_async_engine(settings.database_url, **engine_kwargs)

AsyncSessionLocal = async_sessionmaker(
    engine,
    class_=AsyncSession,
    expire_on_commit=False,
    autocommit=False,
    autoflush=False,
)

from app.data.models.base import Base
import app.data.models.user_model
import app.data.models.space_model
import app.data.models.agent_model
import app.data.models.session_model
import app.data.models.memory_model
import app.data.models.system_setting_model
import app.data.models.calendar_credential_model
import app.data.models.document_model
import app.data.models.gossip_model
import app.data.models.invite_model
import app.data.models.pin_reset_model


# Ensure SQLite enables foreign keys and WAL mode on synchronous driver connection
@event.listens_for(Engine, "connect")
def set_sqlite_pragma(dbapi_connection, connection_record):
    cursor = dbapi_connection.cursor()
    cursor.execute("PRAGMA foreign_keys=ON")
    cursor.execute("PRAGMA busy_timeout=5000")
    if settings.SQLITE_DB_PATH != ":memory:":
        cursor.execute("PRAGMA journal_mode=WAL")
    cursor.close()


def _alembic_config() -> "Config":
    from alembic.config import Config

    backend_root = Path(__file__).resolve().parent.parent.parent
    config = Config(str(backend_root / "alembic.ini"))
    config.set_main_option("script_location", str(backend_root / "alembic"))
    return config


def _migrate(connection) -> None:
    """
    Bring the schema to head, adopting a database that predates Alembic without touching its data.

    A hub that has been running since before this existed already has every table, so `upgrade
    head` would try to create them a second time and fail. It has no `alembic_version` table
    either, which is exactly how that case is recognised: stamp it with the baseline revision —
    the revision that describes the schema it already has — and let the upgrade carry it forward
    from there. A genuinely empty database has no tables to find and is simply migrated.

    Doing this here rather than in a hand-run command is the point: nobody should have to wipe the
    database to pick up an index.
    """
    from alembic import command
    from alembic.runtime.migration import MigrationContext
    from alembic.script import ScriptDirectory

    config = _alembic_config()
    config.attributes["connection"] = connection

    already_stamped = MigrationContext.configure(connection).get_current_revision() is not None
    predates_alembic = not already_stamped and inspect(connection).has_table("users")

    if predates_alembic:
        baseline = ScriptDirectory.from_config(config).get_base()
        command.stamp(config, baseline)

    command.upgrade(config, "head")


async def init_db() -> None:
    """Initialize database tables."""
    async with engine.begin() as conn:
        await conn.run_sync(_migrate)
