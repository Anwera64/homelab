from typing import List, Optional, Protocol
from app.domain.entities.document import StoredDocument


class IDocumentRepository(Protocol):
    async def create(self, document: StoredDocument) -> StoredDocument:
        ...

    async def get_by_id(self, document_id: str) -> Optional[StoredDocument]:
        ...

    async def get_by_user_and_title(self, user_id: str, title: str) -> Optional[StoredDocument]:
        ...

    async def update(self, document: StoredDocument) -> StoredDocument:
        ...

    async def list_by_user(self, user_id: str, space_id: Optional[str] = None) -> List[StoredDocument]:
        ...

    async def delete(self, document_id: str) -> bool:
        ...
