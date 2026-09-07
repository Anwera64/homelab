from fastapi import APIRouter
from app.api.v1.health import router as health_router
from app.api.v1.auth import router as auth_router
from app.api.v1.users import router as users_router
from app.api.v1.spaces import router as spaces_router
from app.api.v1.agents import router as agents_router
from app.api.v1.sessions import router as sessions_router
from app.api.v1.memories import router as memories_router

api_router = APIRouter()
api_router.include_router(health_router)
api_router.include_router(auth_router)
api_router.include_router(users_router)
api_router.include_router(spaces_router)
api_router.include_router(agents_router)
api_router.include_router(sessions_router)
api_router.include_router(memories_router)
