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


class WrongPinException(AuthenticationException):
    """Raised when a member enters the wrong PIN and still has tries left before a wait."""
    def __init__(self, attempts_left: int):
        super().__init__("Wrong PIN")
        self.attempts_left = attempts_left


class WrongConfirmationPinException(WrongPinException):
    """Raised when a signed-in member confirms an action with the wrong PIN. Unlike WrongPinException,
    the member is already authenticated, so this must not read as a sign-out-worthy 401."""
    pass


class PinLockedException(DomainException):
    """Raised while a member has to wait before trying their PIN again."""
    def __init__(self, retry_after_seconds: int):
        super().__init__("Too many wrong PINs. Wait before trying again.")
        self.retry_after_seconds = retry_after_seconds


class CodeGuessesLockedException(DomainException):
    """Raised while the hub has to wait before accepting another invite/reset code guess, hub-wide."""
    def __init__(self, retry_after_seconds: int):
        super().__init__("Too many wrong codes. Wait before trying again.")
        self.retry_after_seconds = retry_after_seconds


class InviteInvalidException(DomainException):
    """Raised for an invite or reset code that is unknown, already used, or expired."""
    pass


class NameTakenException(DomainException):
    """Raised when a chosen name is already in use."""
    pass


class OwnPinResetException(DomainException):
    """Raised when a member tries to approve their own PIN reset request."""
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



class ModelNotConfiguredException(LLMInferenceException):
    """Raised when an agent's model cannot be resolved: no household default, or a pin to a model that is gone."""
    pass
