from fastapi import APIRouter

from app.presentation.api.v1.auth_router import router as auth_router
from app.presentation.api.v1.users_router import router as users_router
from app.presentation.api.v1.invites_router import router as invites_router
from app.presentation.api.v1.spaces_router import router as spaces_router
from app.presentation.api.v1.agents_router import router as agents_router
from app.presentation.api.v1.sessions_router import router as sessions_router
from app.presentation.api.v1.memories_router import router as memories_router
from app.presentation.api.v1.health_router import router as health_router
from app.presentation.api.v1.integrations_router import router as integrations_router
from app.presentation.api.v1.gossip_router import router as gossip_router

api_router = APIRouter()

api_router.include_router(health_router)
api_router.include_router(auth_router)
api_router.include_router(users_router)
api_router.include_router(invites_router)
api_router.include_router(spaces_router)
api_router.include_router(agents_router)
api_router.include_router(sessions_router)
api_router.include_router(memories_router)
api_router.include_router(integrations_router)
api_router.include_router(gossip_router)

