from sqlalchemy import event
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


# Ensure SQLite enables foreign keys and WAL mode on synchronous driver connection
@event.listens_for(Engine, "connect")
def set_sqlite_pragma(dbapi_connection, connection_record):
    cursor = dbapi_connection.cursor()
    cursor.execute("PRAGMA foreign_keys=ON")
    cursor.execute("PRAGMA busy_timeout=5000")
    if settings.SQLITE_DB_PATH != ":memory:":
        cursor.execute("PRAGMA journal_mode=WAL")
    cursor.close()


async def init_db() -> None:
    """Initialize database tables."""
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
