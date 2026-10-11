from typing import Protocol, Dict, Any, Optional


class IPasswordHasher(Protocol):
    def hash(self, password: str) -> str:
        ...

    def verify(self, plain_password: str, hashed_password: str) -> bool:
        ...


class ITokenService(Protocol):
    def create_access_token(
        self, subject: str, is_admin: bool, token_version: int, session_id: Optional[str] = None
    ) -> str:
        """`session_id` is the device session the token belongs to, carried as its `sid` claim."""
        ...

    def decode_token(self, token: str) -> Dict[str, Any]:
        ...
