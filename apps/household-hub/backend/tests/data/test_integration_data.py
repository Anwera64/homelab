import os
import pytest
import pytest_asyncio
from datetime import datetime, timezone
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from sqlalchemy.pool import StaticPool

from app.core.database import Base
from app.domain.entities.integration_credential import CalendarCredential
from app.domain.entities.document import StoredDocument

from app.data.models.calendar_credential_model import CalendarCredentialModel
from app.data.models.document_model import DocumentModel
from app.data.mappers.calendar_credential_data_mapper import CalendarCredentialDataMapper
from app.data.mappers.document_data_mapper import DocumentDataMapper
from app.data.datasources.calendar_credential_data_source import SqliteCalendarCredentialDataSource
from app.data.datasources.document_data_source import SqliteDocumentDataSource
from app.data.repositories.calendar_credential_repository_impl import CalendarCredentialRepositoryImpl
from app.data.repositories.document_repository_impl import DocumentRepositoryImpl
from app.data.security.secret_cipher_impl import SecretCipherImpl


@pytest_asyncio.fixture
async def data_db_session():
    test_engine = create_async_engine(
        "sqlite+aiosqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    async with test_engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    session_maker = async_sessionmaker(test_engine, class_=AsyncSession, expire_on_commit=False)
    async with session_maker() as session:
        yield session

    async with test_engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)


def test_secret_cipher_encryption_roundtrip():
    cipher = SecretCipherImpl(secret_key="my-super-secret-key-that-is-at-least-32-chars-long")
    original = "Apple-App-Specific-Password-123!"
    encrypted = cipher.encrypt(original)
    assert encrypted != original
    assert len(encrypted) > 20

    decrypted = cipher.decrypt(encrypted)
    assert decrypted == original

    # Tampered ciphertext raises SecretDecryptionException
    from app.domain.exceptions import SecretDecryptionException
    with pytest.raises(SecretDecryptionException):
        cipher.decrypt("tampered" + encrypted[8:])


@pytest.mark.asyncio
async def test_calendar_credential_repository(data_db_session: AsyncSession):
    # Insert parent user to satisfy foreign key
    from app.data.models.user_model import UserModel
    test_user = UserModel(
        id="user-42",
        username="user42",
        email="user42@homelab.local",
        full_name="User 42",
        hashed_password="hash",
    )
    data_db_session.add(test_user)
    await data_db_session.commit()

    mapper = CalendarCredentialDataMapper()
    ds = SqliteCalendarCredentialDataSource(data_db_session)
    repo = CalendarCredentialRepositoryImpl(ds, mapper)

    cred = CalendarCredential(
        user_id="user-42",
        provider="apple_icloud",
        url="https://caldav.icloud.com",
        username="user@icloud.com",
        encrypted_secret="enc-pwd-xyz",
        calendar_name="Family",
    )

    # 1. Save
    saved = await repo.save(cred)
    await data_db_session.commit()
    assert saved.user_id == "user-42"
    assert saved.calendar_name == "Family"

    # 2. Get by user id
    fetched = await repo.get_by_user_id("user-42")
    assert fetched is not None
    assert fetched.username == "user@icloud.com"
    assert fetched.calendar_name == "Family"

    # 3. Save again (updates existing user credential)
    cred.calendar_name = "Family Updated"
    updated = await repo.save(cred)
    await data_db_session.commit()
    assert updated.calendar_name == "Family Updated"

    # 4. Delete
    deleted = await repo.delete_by_user_id("user-42")
    await data_db_session.commit()
    assert deleted is True
    assert (await repo.get_by_user_id("user-42")) is None


@pytest.mark.asyncio
async def test_document_repository(data_db_session: AsyncSession):
    # Insert parent user and space to satisfy foreign key
    from app.data.models.user_model import UserModel
    from app.data.models.space_model import SpaceModel
    test_user = UserModel(
        id="user-10",
        username="user10",
        email="user10@homelab.local",
        full_name="User 10",
        hashed_password="hash",
    )
    test_space = SpaceModel(
        id="space-shared",
        name="Shared Space",
        type="shared",
    )
    data_db_session.add(test_user)
    data_db_session.add(test_space)
    await data_db_session.commit()

    mapper = DocumentDataMapper()
    ds = SqliteDocumentDataSource(data_db_session)
    repo = DocumentRepositoryImpl(ds, mapper)

    doc = StoredDocument(
        user_id="user-10",
        space_id="space-shared",
        title="Homelab Architecture Notes",
        content="# Overview\nFirst draft",
        format="markdown",
        version=1,
    )

    # 1. Create
    created = await repo.create(doc)
    await data_db_session.commit()
    assert created.id == doc.id
    assert created.version == 1

    # 2. Get by id
    by_id = await repo.get_by_id(created.id)
    assert by_id is not None
    assert by_id.title == "Homelab Architecture Notes"

    # 3. Get by user and title
    by_title = await repo.get_by_user_and_title("user-10", "Homelab Architecture Notes")
    assert by_title is not None
    assert by_title.id == created.id

    # 4. Update
    by_title.content = "# Overview\nUpdated draft"
    by_title.version = 2
    updated = await repo.update(by_title)
    await data_db_session.commit()
    assert updated.version == 2
    assert updated.content == "# Overview\nUpdated draft"

    # 5. List by user
    docs = await repo.list_by_user("user-10")
    assert len(docs) == 1

    # 6. Delete
    deleted = await repo.delete(created.id)
    await data_db_session.commit()
    assert deleted is True
    assert (await repo.get_by_id(created.id)) is None
