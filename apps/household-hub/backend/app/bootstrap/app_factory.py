import asyncio
import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.core.config import settings
from app.core.database import init_db, AsyncSessionLocal
from app.presentation.api.v1.router import api_router
from app.presentation.api.exception_handlers import register_exception_handlers
from app.presentation.api import deps as pres_deps
from app.bootstrap.di import setup_dependency_injection, get_container
from app.domain.use_cases.agents.seed_builtin_agents import SeedBuiltinAgentsUseCase
from app.domain.use_cases.models.sync_default_model import SyncDefaultModelUseCase
from app.domain.use_cases.agents.purge_expired_trash_agents import PurgeExpiredTrashAgentsUseCase
from app import __version__

logger = logging.getLogger("household_hub.bootstrap")


async def periodic_trash_purger(interval_seconds: int = 3600):
    """Autonomous background task to periodically purge expired trash models and archive their sessions."""
    while True:
        try:
            await asyncio.sleep(interval_seconds)
            async with AsyncSessionLocal() as session:
                container = get_container(session)
                purged_ids = await container[PurgeExpiredTrashAgentsUseCase].execute()
                if purged_ids:
                    logger.info("Periodic trash purger archived & cleaned up %d expired agents: %s", len(purged_ids), purged_ids)
        except asyncio.CancelledError:
            break
        except Exception as e:
            logger.error("Error in periodic trash purger: %s", e, exc_info=True)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Initialize DB schemas, seed baseline models, and start background workers on startup
    bg_task = None
    if settings.ENVIRONMENT != "testing":
        await init_db()
        async with AsyncSessionLocal() as session:
            container = get_container(session)
            await container[SyncDefaultModelUseCase].execute(settings.DEFAULT_LLM_MODEL)
            await container[SeedBuiltinAgentsUseCase].execute()
            await container[pres_deps.get_shared_space_use_case].execute()
            await container[PurgeExpiredTrashAgentsUseCase].execute()
        bg_task = asyncio.create_task(periodic_trash_purger())

    try:
        yield
    finally:
        if bg_task and not bg_task.done():
            bg_task.cancel()
            try:
                await bg_task
            except asyncio.CancelledError:
                pass
        from app.bootstrap.di import _searxng_connector, _ollama_connector
        await _searxng_connector.close()
        await _ollama_connector.close()



def create_app() -> FastAPI:
    app = FastAPI(
        title=settings.PROJECT_NAME,
        version=__version__,
        openapi_url=f"{settings.API_V1_STR}/openapi.json",
        lifespan=lifespan,
    )

    # Configure CORS
    origins = settings.CORS_ORIGINS
    if isinstance(origins, str):
        origins = [origins]

    app.add_middleware(
        CORSMiddleware,
        allow_origins=origins,
        allow_origin_regex=r"^https://([a-zA-Z0-9-]+\.)?spicy-llama\.duckdns\.org$",
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # Register Clean Architecture exception handlers
    register_exception_handlers(app)

    # Include Presentation REST API
    app.include_router(api_router, prefix=settings.API_V1_STR)

    # Root status endpoint
    @app.get("/", tags=["Root"])
    async def root():
        return {
            "name": settings.PROJECT_NAME,
            "version": __version__,
            "docs_url": "/docs",
            "api": settings.API_V1_STR,
        }

    # Setup Dependency Injection
    setup_dependency_injection(app)

    return app
