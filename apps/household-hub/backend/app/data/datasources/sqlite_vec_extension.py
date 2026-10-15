"""
Loads `sqlite-vec` on every database connection (memory spec §4.2).

The extension is an accelerator for ranking memory vectors, not a requirement: if it can't load,
the vector data source ranks the same rows in Python. SQL-level extension loading is switched off
again straight after, so no query can load anything.
"""

import logging

from sqlalchemy import event
from sqlalchemy.engine import Engine

logger = logging.getLogger(__name__)

# Switched off by a test that needs a connection without the extension.
ENABLED = True

_state = {"loaded": None}


def loaded() -> bool | None:
    """Whether the last connection opened loaded the extension; None before any has been opened."""
    return _state["loaded"]


async def _load_async(connection) -> None:
    import sqlite_vec

    await connection.enable_load_extension(True)
    try:
        await connection.load_extension(sqlite_vec.loadable_path())
    finally:
        await connection.enable_load_extension(False)


def _load_sync(connection) -> None:
    import sqlite_vec

    connection.enable_load_extension(True)
    try:
        sqlite_vec.load(connection)
    finally:
        connection.enable_load_extension(False)


def _on_connect(dbapi_connection, connection_record) -> None:
    if not ENABLED:
        return
    try:
        if hasattr(dbapi_connection, "run_async"):
            # aiosqlite behind SQLAlchemy's adapter: its own methods are coroutines.
            dbapi_connection.run_async(_load_async)
        else:
            _load_sync(dbapi_connection)
    except Exception as exc:
        if _state["loaded"] is not False:
            logger.warning("Memory search | sqlite-vec did not load, ranking in Python | error=%s", exc)
        _state["loaded"] = False
        return
    _state["loaded"] = True


def install() -> None:
    """Hooks every engine's new connections. Safe to call more than once."""
    if not event.contains(Engine, "connect", _on_connect):
        event.listen(Engine, "connect", _on_connect)
