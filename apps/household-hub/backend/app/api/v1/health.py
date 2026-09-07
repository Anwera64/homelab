from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import text
from app import __version__
from app.api.deps import get_db

router = APIRouter()


@router.get("/health", tags=["Health"])
async def healthcheck(db: AsyncSession = Depends(get_db)):
    """Check backend operational status and database connection."""
    await db.execute(text("SELECT 1"))
    return {
        "status": "ok",
        "database": "connected",
        "version": __version__,
    }
