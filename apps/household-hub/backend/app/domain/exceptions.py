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


# Integration Exceptions
class CalendarIntegrationException(DomainException):
    """Base exception for calendar operations."""
    pass


class CalendarAuthException(CalendarIntegrationException):
    """Raised when CalDAV authentication fails."""
    pass


class SearchServiceException(DomainException):
    """Raised when SearXNG query fails or times out."""
    pass


class DocumentParsingException(DomainException):
    """Raised when a document cannot be parsed."""
    pass


class ScannedPdfException(DocumentParsingException):
    """Raised when a PDF has zero text characters (pure raster scan)."""
    pass


class DocumentNotFoundException(EntityNotFoundException):
    """Raised when a stored document is not found."""
    pass


class ToolNotFoundException(DomainException):
    """Raised when an unknown tool is requested."""
    pass


class ToolPermissionDeniedException(DomainException):
    """Raised when an agent or session lacks permission for a tool."""
    pass


class SecretModeLockException(ToolPermissionDeniedException):
    """Raised when external write tools are invoked inside a Secret Mode session."""
    pass


class SecretDecryptionException(DomainException):
    """Raised when stored encrypted secrets cannot be decrypted (e.g. invalid token, key mismatch)."""
    pass


# Stage 3 AI & Gossip Exceptions
class LLMInferenceException(DomainException):
    """Raised when LLM inference fails, times out, or returns an error."""
    pass


class GossipPublicationException(DomainException):
    """Raised when publishing a milestone to the Gossip Bus fails."""
    pass


class PrivacyTriggerException(DomainException):
    """Raised when natural language privacy triggers conflict with an action."""
    pass


class SessionBusyException(DomainException):
    """Raised when a concurrent turn is already processing for a session."""
    pass

