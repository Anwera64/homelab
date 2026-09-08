from typing import List, Optional
from app.domain.entities.document import StoredDocument
from app.domain.repositories.document_repository import IDocumentRepository
from app.data.datasources.document_data_source import SqliteDocumentDataSource
from app.data.mappers.document_data_mapper import DocumentDataMapper


class DocumentRepositoryImpl(IDocumentRepository):
    def __init__(self, data_source: SqliteDocumentDataSource, mapper: DocumentDataMapper):
        self.data_source = data_source
        self.mapper = mapper

    async def create(self, document: StoredDocument) -> StoredDocument:
        model = self.mapper.to_model(document)
        created_model = await self.data_source.create(model)
        return self.mapper.to_domain(created_model)

    async def get_by_id(self, document_id: str) -> Optional[StoredDocument]:
        model = await self.data_source.get_by_id(document_id)
        if not model:
            return None
        return self.mapper.to_domain(model)

    async def get_by_user_and_title(self, user_id: str, title: str) -> Optional[StoredDocument]:
        model = await self.data_source.get_by_user_and_title(user_id=user_id, title=title)
        if not model:
            return None
        return self.mapper.to_domain(model)

    async def update(self, document: StoredDocument) -> StoredDocument:
        model = self.mapper.to_model(document)
        updated_model = await self.data_source.update(model)
        return self.mapper.to_domain(updated_model)

    async def list_by_user(self, user_id: str, space_id: Optional[str] = None) -> List[StoredDocument]:
        models = await self.data_source.list_by_user(user_id=user_id, space_id=space_id)
        return [self.mapper.to_domain(m) for m in models]

    async def delete(self, document_id: str) -> bool:
        return await self.data_source.delete(document_id)
