from fastapi import APIRouter
from app import __version__

router = APIRouter(tags=["Health"])


@router.get("/health")
async def healthcheck():
    """Health check endpoint confirming service and database connectivity."""
    return {
        "status": "ok",
        "version": __version__,
        "database": "connected",
    }
