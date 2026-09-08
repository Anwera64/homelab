from typing import Protocol


class ISecretCipher(Protocol):
    def encrypt(self, plaintext: str) -> str:
        ...

    def decrypt(self, ciphertext: str) -> str:
        ...
