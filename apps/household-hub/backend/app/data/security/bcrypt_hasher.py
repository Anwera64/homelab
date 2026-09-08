from app.domain.repositories.security_service import IPasswordHasher
import app.core.security as security


class BcryptPasswordHasher(IPasswordHasher):
    def hash(self, password: str) -> str:
        return security.get_password_hash(password)

    def verify(self, plain_password: str, hashed_password: str) -> bool:
        return security.verify_password(plain_password, hashed_password)
