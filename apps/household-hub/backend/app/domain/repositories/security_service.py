from typing import Protocol, Dict, Any


class IPasswordHasher(Protocol):
    def hash(self, password: str) -> str:
        ...

    def verify(self, plain_password: str, hashed_password: str) -> bool:
        ...


class ITokenService(Protocol):
    def create_access_token(self, subject: str, is_admin: bool) -> str:
        ...

    def decode_token(self, token: str) -> Dict[str, Any]:
        ...
