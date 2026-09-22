"""
Alembic's entry point.

Two things here are deliberate. First, the engine is **synchronous** (`sqlite://`, not
`sqlite+aiosqlite://`): Alembic's autogenerate and its migration context both want a plain
connection, and nothing about applying DDL to a local file benefits from an event loop. The hub
calls `upgrade head` from a worker thread at startup so the loop is never blocked.

Second, `render_as_batch` is on. SQLite cannot `ALTER TABLE ... ALTER COLUMN`, so any future
revision that changes or drops a column has to rebuild the table; batch mode makes Alembic write
that rebuild instead of emitting DDL SQLite will reject.
"""

from logging.config import fileConfig

from alembic import context
from sqlalchemy import create_engine, pool

from app.core.config import settings

# Importing this module imports every ORM model, which is what populates Base.metadata.
from app.core.database import Base

config = context.config

# Only when Alembic is the program being run. The hub migrates inside its own startup, and
# `fileConfig` disables every logger that already exists unless told otherwise — which took
# uvicorn's loggers down with it, `uvicorn.access` included, for the life of the process. The hub
# went on serving perfectly and said nothing about it, which is a bad way to find out.
if config.config_file_name is not None and config.attributes.get("connection") is None:
    fileConfig(config.config_file_name, disable_existing_loggers=False)

target_metadata = Base.metadata


def _database_url() -> str:
    """The URL Alembic should migrate: whatever was passed in, else the app's own database."""
    configured = config.get_main_option("sqlalchemy.url")
    if configured:
        return configured
    if settings.SQLITE_DB_PATH == ":memory:":
        return "sqlite://"
    return f"sqlite:///{settings.SQLITE_DB_PATH}"


def run_migrations_offline() -> None:
    context.configure(
        url=_database_url(),
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
        compare_type=True,
        render_as_batch=True,
    )

    with context.begin_transaction():
        context.run_migrations()


def _run(connection) -> None:
    context.configure(
        connection=connection,
        target_metadata=target_metadata,
        compare_type=True,
        render_as_batch=True,
    )

    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online() -> None:
    # The hub migrates on startup and hands its own connection in, so the DDL runs inside the
    # transaction it already opened. A second engine against the same SQLite file would sit behind
    # that transaction's write lock and wait for a connection that is waiting for it.
    existing_connection = config.attributes.get("connection")
    if existing_connection is not None:
        _run(existing_connection)
        return

    engine = create_engine(_database_url(), poolclass=pool.NullPool)

    try:
        with engine.connect() as connection:
            _run(connection)
    finally:
        engine.dispose()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
