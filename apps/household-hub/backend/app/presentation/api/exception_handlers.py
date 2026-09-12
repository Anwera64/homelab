from fastapi import FastAPI, Request, status
from fastapi.responses import JSONResponse

from app.domain.exceptions import (
    DomainException,
    EntityNotFoundException,
    AuthenticationException,
    WrongPinException,
    PinLockedException,
    ZeroLeakViolationException,
    SoleAdminDeletionException,
    SlugConflictException,
    TrashGracePeriodException,
    SecretModeViolationException,
    InvalidOperationException,
    CalendarIntegrationException,
    CalendarAuthException,
    SearchServiceException,
    DocumentParsingException,
    ToolNotFoundException,
    ToolPermissionDeniedException,
    SecretDecryptionException,
    LLMInferenceException,
)


def register_exception_handlers(app: FastAPI) -> None:
    @app.exception_handler(AuthenticationException)
    async def authentication_handler(request: Request, exc: AuthenticationException):
        return JSONResponse(
            status_code=status.HTTP_401_UNAUTHORIZED,
            content={"detail": str(exc)},
            headers={"WWW-Authenticate": "Bearer"},
        )

    @app.exception_handler(WrongPinException)
    async def wrong_pin_handler(request: Request, exc: WrongPinException):
        return JSONResponse(
            status_code=status.HTTP_401_UNAUTHORIZED,
            content={"detail": str(exc), "attempts_left": exc.attempts_left},
            headers={"WWW-Authenticate": "Bearer"},
        )

    @app.exception_handler(PinLockedException)
    async def pin_locked_handler(request: Request, exc: PinLockedException):
        return JSONResponse(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            content={"detail": str(exc), "retry_after_seconds": exc.retry_after_seconds},
            headers={"Retry-After": str(exc.retry_after_seconds)},
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

    @app.exception_handler(CalendarAuthException)
    async def calendar_auth_handler(request: Request, exc: CalendarAuthException):
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={"detail": str(exc)},
        )

    @app.exception_handler(CalendarIntegrationException)
    async def calendar_integration_handler(request: Request, exc: CalendarIntegrationException):
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={"detail": str(exc)},
        )

    @app.exception_handler(ToolPermissionDeniedException)
    async def tool_permission_handler(request: Request, exc: ToolPermissionDeniedException):
        return JSONResponse(
            status_code=status.HTTP_403_FORBIDDEN,
            content={"detail": str(exc)},
        )

    @app.exception_handler(ToolNotFoundException)
    async def tool_not_found_handler(request: Request, exc: ToolNotFoundException):
        return JSONResponse(
            status_code=status.HTTP_404_NOT_FOUND,
            content={"detail": str(exc)},
        )

    @app.exception_handler(DocumentParsingException)
    async def document_parsing_handler(request: Request, exc: DocumentParsingException):
        return JSONResponse(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            content={"detail": str(exc)},
        )

    @app.exception_handler(SearchServiceException)
    async def search_service_handler(request: Request, exc: SearchServiceException):
        return JSONResponse(
            status_code=status.HTTP_502_BAD_GATEWAY,
            content={"detail": str(exc)},
        )

    @app.exception_handler(SecretDecryptionException)
    async def secret_decryption_handler(request: Request, exc: SecretDecryptionException):
        return JSONResponse(
            status_code=status.HTTP_401_UNAUTHORIZED,
            content={"detail": "Stored credentials could not be decrypted. Please reconfigure your calendar."},
        )

    @app.exception_handler(LLMInferenceException)
    async def llm_inference_handler(request: Request, exc: LLMInferenceException):
        error_msg = str(exc)
        if "timed out" in error_msg.lower():
            return JSONResponse(
                status_code=status.HTTP_504_GATEWAY_TIMEOUT,
                content={"detail": error_msg},
            )
        return JSONResponse(
            status_code=status.HTTP_502_BAD_GATEWAY,
            content={"detail": error_msg},
        )

    @app.exception_handler(DomainException)
    async def general_domain_handler(request: Request, exc: DomainException):
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={"detail": str(exc)},
        )
