from fastapi import FastAPI, Request, status
from fastapi.responses import JSONResponse

from app.domain.exceptions import (
    DomainException,
    EntityNotFoundException,
    AuthenticationException,
    ZeroLeakViolationException,
    SoleAdminDeletionException,
    SlugConflictException,
    TrashGracePeriodException,
    SecretModeViolationException,
    InvalidOperationException,
)


def register_exception_handlers(app: FastAPI) -> None:
    @app.exception_handler(AuthenticationException)
    async def authentication_handler(request: Request, exc: AuthenticationException):
        return JSONResponse(
            status_code=status.HTTP_401_UNAUTHORIZED,
            content={"detail": str(exc)},
            headers={"WWW-Authenticate": "Bearer"},
        )

    @app.exception_handler(EntityNotFoundException)
    async def entity_not_found_handler(request: Request, exc: EntityNotFoundException):
        return JSONResponse(
            status_code=status.HTTP_404_NOT_FOUND,
            content={"detail": str(exc)},
        )

    @app.exception_handler(ZeroLeakViolationException)
    async def zero_leak_handler(request: Request, exc: ZeroLeakViolationException):
        return JSONResponse(
            status_code=status.HTTP_403_FORBIDDEN,
            content={"detail": str(exc)},
        )

    @app.exception_handler(TrashGracePeriodException)
    async def trash_grace_period_handler(request: Request, exc: TrashGracePeriodException):
        return JSONResponse(
            status_code=status.HTTP_410_GONE,
            content={"detail": str(exc)},
        )

    @app.exception_handler(SoleAdminDeletionException)
    async def sole_admin_deletion_handler(request: Request, exc: SoleAdminDeletionException):
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={"detail": str(exc)},
        )

    @app.exception_handler(SlugConflictException)
    async def slug_conflict_handler(request: Request, exc: SlugConflictException):
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={"detail": str(exc)},
        )

    @app.exception_handler(SecretModeViolationException)
    async def secret_mode_handler(request: Request, exc: SecretModeViolationException):
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={"detail": str(exc)},
        )

    @app.exception_handler(InvalidOperationException)
    async def invalid_operation_handler(request: Request, exc: InvalidOperationException):
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={"detail": str(exc)},
        )

    @app.exception_handler(DomainException)
    async def general_domain_handler(request: Request, exc: DomainException):
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={"detail": str(exc)},
        )
