from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.core.config import settings
from app.core.database import init_db, AsyncSessionLocal
from app.services.catalog_seeder import seed_builtin_agents
from app.services.spaces_service import get_or_create_shared_space
from app.api.v1.router import api_router
from app import __version__


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Initialize DB schemas and seed baseline models on startup
    if settings.ENVIRONMENT != "testing":
        await init_db()
        async with AsyncSessionLocal() as session:
            await seed_builtin_agents(session)
            await get_or_create_shared_space(session)
            await session.commit()
    yield



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

app.include_router(api_router, prefix=settings.API_V1_STR)


@app.get("/", tags=["Root"])
async def root():
    return {
        "name": settings.PROJECT_NAME,
        "version": __version__,
        "docs_url": "/docs",
        "api": settings.API_V1_STR,
    }
