from typing import List, Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, delete
from app.data.models.document_model import DocumentModel


class SqliteDocumentDataSource:
    def __init__(self, session: AsyncSession):
        self.session = session

    async def create(self, model: DocumentModel) -> DocumentModel:
        self.session.add(model)
        await self.session.flush()
        return model

    async def get_by_id(self, document_id: str) -> Optional[DocumentModel]:
        stmt = select(DocumentModel).where(DocumentModel.id == document_id)
        result = await self.session.execute(stmt)
        return result.scalar_one_or_none()

    async def get_by_user_and_title(self, user_id: str, title: str) -> Optional[DocumentModel]:
        stmt = select(DocumentModel).where(
            DocumentModel.user_id == user_id,
            DocumentModel.title == title,
        )
        result = await self.session.execute(stmt)
        return result.scalar_one_or_none()

    async def update(self, model: DocumentModel) -> DocumentModel:
        # Merges or updates state within session
        existing = await self.get_by_id(model.id)
        if existing:
            existing.title = model.title
            existing.content = model.content
            existing.format = model.format
            existing.version = model.version
            existing.space_id = model.space_id
            existing.updated_at = model.updated_at
            return existing
        self.session.add(model)
        await self.session.flush()
        return model

    async def list_by_user(self, user_id: str, space_id: Optional[str] = None) -> List[DocumentModel]:
        stmt = select(DocumentModel).where(DocumentModel.user_id == user_id)
        if space_id is not None:
            stmt = stmt.where(DocumentModel.space_id == space_id)
        stmt = stmt.order_by(DocumentModel.updated_at.desc())
        result = await self.session.execute(stmt)
        return list(result.scalars().all())

    async def delete(self, document_id: str) -> bool:
        stmt = delete(DocumentModel).where(DocumentModel.id == document_id)
        result = await self.session.execute(stmt)
        return result.rowcount > 0
