class DomainException(Exception):
    """Base exception for all domain errors."""
    def __init__(self, message: str = "A domain error occurred."):
        super().__init__(message)
        self.message = message


class EntityNotFoundException(DomainException):
    """Raised when an entity is not found."""
    pass


class AuthenticationException(DomainException):
    """Raised when authentication fails due to invalid credentials or token."""
    pass


class ZeroLeakViolationException(DomainException):
    """Raised when a user attempts to access private data of another user."""
    pass


class SoleAdminDeletionException(DomainException):
    """Raised when attempting to delete the only administrator account."""
    pass


class SlugConflictException(DomainException):
    """Raised when an agent slug conflicts with an existing active or trashed agent."""
    pass


class TrashGracePeriodException(DomainException):
    """Raised when interacting with an agent in trash or when grace period has expired."""
    pass


class SecretModeViolationException(DomainException):
    """Raised when attempting to leak secret session facts into shared household scope."""
    pass


class InvalidOperationException(DomainException):
    """Raised when a domain operation is invalid under current state."""
    pass
