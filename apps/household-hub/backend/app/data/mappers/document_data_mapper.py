from app.domain.entities.document import StoredDocument
from app.data.models.document_model import DocumentModel


class DocumentDataMapper:
    @staticmethod
    def to_domain(model: DocumentModel) -> StoredDocument:
        return StoredDocument(
            id=model.id,
            user_id=model.user_id,
            space_id=model.space_id,
            title=model.title,
            content=model.content,
            format=model.format,
            version=model.version,
            created_at=model.created_at,
            updated_at=model.updated_at,
        )

    @staticmethod
    def to_model(entity: StoredDocument) -> DocumentModel:
        return DocumentModel(
            id=entity.id,
            user_id=entity.user_id,
            space_id=entity.space_id,
            title=entity.title,
            content=entity.content,
            format=entity.format,
            version=entity.version,
            created_at=entity.created_at,
            updated_at=entity.updated_at,
        )
