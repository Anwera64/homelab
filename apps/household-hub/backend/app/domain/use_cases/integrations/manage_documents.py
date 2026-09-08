from datetime import datetime, timezone
from typing import List, Optional
from app.domain.entities.document import StoredDocument
from app.domain.exceptions import DocumentNotFoundException, InvalidOperationException
from app.domain.repositories.document_repository import IDocumentRepository
from app.domain.repositories.unit_of_work import IUnitOfWork


class SaveDocumentUseCase:
    def __init__(self, document_repo: IDocumentRepository, uow: IUnitOfWork):
        self.document_repo = document_repo
        self.uow = uow

    async def execute(
        self,
        user_id: str,
        title: str,
        content: str,
        action: str = "create",  # "create" | "append" | "replace"
        space_id: Optional[str] = None,
        format: str = "markdown",
    ) -> StoredDocument:
        if action not in {"create", "append", "replace"}:
            raise InvalidOperationException(f"Invalid document action '{action}'. Allowed: create, append, replace")

        async with self.uow:
            existing = await self.document_repo.get_by_user_and_title(user_id=user_id, title=title)

            if action == "create":
                if existing:
                    # Update version if replacing or append; for create we increment title or replace
                    existing.content = content
                    existing.version += 1
                    existing.updated_at = datetime.now(timezone.utc)
                    saved = await self.document_repo.update(existing)
                else:
                    doc = StoredDocument(
                        user_id=user_id,
                        space_id=space_id,
                        title=title,
                        content=content,
                        format=format,
                        version=1,
                    )
                    saved = await self.document_repo.create(doc)

            elif action == "append":
                if not existing:
                    doc = StoredDocument(
                        user_id=user_id,
                        space_id=space_id,
                        title=title,
                        content=content,
                        format=format,
                        version=1,
                    )
                    saved = await self.document_repo.create(doc)
                else:
                    existing.content = existing.content + content
                    existing.version += 1
                    existing.updated_at = datetime.now(timezone.utc)
                    saved = await self.document_repo.update(existing)

            elif action == "replace":
                if not existing:
                    doc = StoredDocument(
                        user_id=user_id,
                        space_id=space_id,
                        title=title,
                        content=content,
                        format=format,
                        version=1,
                    )
                    saved = await self.document_repo.create(doc)
                else:
                    existing.content = content
                    existing.version += 1
                    existing.updated_at = datetime.now(timezone.utc)
                    saved = await self.document_repo.update(existing)

            await self.uow.commit()

        return saved


class GetDocumentUseCase:
    def __init__(self, document_repo: IDocumentRepository):
        self.document_repo = document_repo

    async def execute(self, document_id: str, user_id: str) -> StoredDocument:
        doc = await self.document_repo.get_by_id(document_id)
        if not doc or doc.user_id != user_id:
            raise DocumentNotFoundException(f"Document with id '{document_id}' not found.")
        return doc


class ListDocumentsUseCase:
    def __init__(self, document_repo: IDocumentRepository):
        self.document_repo = document_repo

    async def execute(self, user_id: str, space_id: Optional[str] = None) -> List[StoredDocument]:
        return await self.document_repo.list_by_user(user_id=user_id, space_id=space_id)


class DeleteDocumentUseCase:
    def __init__(self, document_repo: IDocumentRepository, uow: IUnitOfWork):
        self.document_repo = document_repo
        self.uow = uow

    async def execute(self, document_id: str, user_id: str) -> bool:
        doc = await self.document_repo.get_by_id(document_id)
        if not doc or doc.user_id != user_id:
            raise DocumentNotFoundException(f"Document with id '{document_id}' not found.")

        async with self.uow:
            deleted = await self.document_repo.delete(document_id)
            await self.uow.commit()
        return deleted
