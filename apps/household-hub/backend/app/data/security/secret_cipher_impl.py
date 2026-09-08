import base64
import hashlib
from cryptography.fernet import Fernet, InvalidToken
from app.domain.exceptions import SecretDecryptionException
from app.domain.repositories.secret_cipher import ISecretCipher


class SecretCipherImpl(ISecretCipher):
    """AES-256 / Fernet implementation of secret cipher using key derivation."""

    def __init__(self, secret_key: str):
        # Derive standard 32-byte key for Fernet using SHA-256
        key_bytes = hashlib.sha256(secret_key.encode("utf-8")).digest()
        fernet_key = base64.urlsafe_b64encode(key_bytes)
        self.fernet = Fernet(fernet_key)

    def encrypt(self, plaintext: str) -> str:
        return self.fernet.encrypt(plaintext.encode("utf-8")).decode("utf-8")

    def decrypt(self, ciphertext: str) -> str:
        try:
            return self.fernet.decrypt(ciphertext.encode("utf-8")).decode("utf-8")
        except (InvalidToken, Exception) as exc:
            raise SecretDecryptionException("Failed to decrypt secret. The encryption key or token may be invalid.") from exc
