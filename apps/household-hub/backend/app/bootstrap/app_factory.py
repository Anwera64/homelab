from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.core.config import settings
from app.core.database import init_db, AsyncSessionLocal
from app.presentation.api.v1.router import api_router
from app.presentation.api.exception_handlers import register_exception_handlers
from app.bootstrap.di import setup_dependency_injection, get_container
from app.domain.use_cases.agents.seed_builtin_agents import SeedBuiltinAgentsUseCase
from app.data.datasources.agent_data_source import SqliteAgentDataSource
from app.data.repositories.agent_repository_impl import AgentRepositoryImpl
from app.data.mappers.agent_data_mapper import AgentDataMapper
from app.data.datasources.space_data_source import SqliteSpaceDataSource
from app.data.repositories.space_repository_impl import SpaceRepositoryImpl
from app.data.mappers.space_data_mapper import SpaceDataMapper
from app.domain.use_cases.spaces.get_shared_space import GetSharedSpaceUseCase
from app.data.persistence.unit_of_work import SqliteUnitOfWork
from app import __version__


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Initialize DB schemas and seed baseline models on startup
    if settings.ENVIRONMENT != "testing":
        await init_db()
        async with AsyncSessionLocal() as session:
            uow = SqliteUnitOfWork(session)
            agent_repo = AgentRepositoryImpl(SqliteAgentDataSource(session), AgentDataMapper())
            space_repo = SpaceRepositoryImpl(SqliteSpaceDataSource(session), SpaceDataMapper())
            
            await SeedBuiltinAgentsUseCase(agent_repo, uow).execute()
            await GetSharedSpaceUseCase(space_repo, uow).execute()
    yield


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
        allow_origins=origins if origins != ["*"] else ["*"],
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
