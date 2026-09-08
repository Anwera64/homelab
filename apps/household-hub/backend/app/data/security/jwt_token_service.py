from datetime import datetime, timezone, timedelta
from typing import Dict, Any, Optional
import jwt

from app.domain.repositories.security_service import ITokenService
from app.domain.exceptions import InvalidOperationException


class JwtTokenService(ITokenService):
    def __init__(self, secret_key: str, algorithm: str = "HS256", expire_minutes: int = 1440):
        self.secret_key = secret_key
        self.algorithm = algorithm
        self.expire_minutes = expire_minutes

    def create_access_token(self, subject: str, is_admin: bool, expires_delta: Optional[timedelta] = None) -> str:
        now = datetime.now(timezone.utc)
        if expires_delta:
            expire = now + expires_delta
        else:
            expire = now + timedelta(minutes=self.expire_minutes)

        to_encode: Dict[str, Any] = {
            "sub": str(subject),
            "is_admin": is_admin,
            "exp": expire,
            "iat": now,
        }
        return jwt.encode(to_encode, self.secret_key, algorithm=self.algorithm)

    def decode_token(self, token: str) -> Dict[str, Any]:
        try:
            payload = jwt.decode(token, self.secret_key, algorithms=[self.algorithm])
            return payload
        except jwt.PyJWTError as e:
            raise InvalidOperationException(f"Could not validate credentials: {e}")
