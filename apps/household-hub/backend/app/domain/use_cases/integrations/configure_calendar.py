from typing import Optional

from app.domain.entities.integration_credential import CalendarCredential
from app.domain.exceptions import CalendarAuthException
from app.domain.repositories.calendar_credential_repository import ICalendarCredentialRepository
from app.domain.repositories.calendar_connector import ICalendarConnector
from app.domain.repositories.secret_cipher import ISecretCipher
from app.domain.repositories.unit_of_work import IUnitOfWork
from app.domain.use_cases.integrations.mark_calendar_steps_fixed import MarkCalendarStepsFixedUseCase


class ConfigureCalendarUseCase:
    def __init__(
        self,
        credential_repo: ICalendarCredentialRepository,
        connector: ICalendarConnector,
        cipher: ISecretCipher,
        uow: IUnitOfWork,
        mark_fixed: Optional[MarkCalendarStepsFixedUseCase] = None,
    ):
        self.credential_repo = credential_repo
        self.connector = connector
        self.cipher = cipher
        self.uow = uow
        self.mark_fixed = mark_fixed

    async def execute(
        self,
        user_id: str,
        provider: str,
        url: str,
        username: str,
        password: str,
        calendar_name: str = "Default",
    ) -> CalendarCredential:
        # Create candidate credential
        encrypted_secret = self.cipher.encrypt(password)
        candidate = CalendarCredential(
            user_id=user_id,
            provider=provider,
            url=url,
            username=username,
            encrypted_secret=encrypted_secret,
            calendar_name=calendar_name,
        )

        # Test CalDAV connection
        is_valid = await self.connector.test_connection(candidate, password)
        if not is_valid:
            raise CalendarAuthException(f"Failed to authenticate with CalDAV server at {url}")

        # Persist (replaces existing if present for single calendar model)
        async with self.uow:
            saved = await self.credential_repo.save(candidate)
            # The calendar steps that failed for want of one now read as fixed, saved together with it.
            if self.mark_fixed is not None:
                await self.mark_fixed.execute(user_id)
            await self.uow.commit()

        return saved
