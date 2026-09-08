from app.domain.repositories.calendar_credential_repository import ICalendarCredentialRepository
from app.domain.repositories.unit_of_work import IUnitOfWork


class DeleteCalendarUseCase:
    def __init__(self, credential_repo: ICalendarCredentialRepository, uow: IUnitOfWork):
        self.credential_repo = credential_repo
        self.uow = uow

    async def execute(self, user_id: str) -> bool:
        async with self.uow:
            deleted = await self.credential_repo.delete_by_user_id(user_id)
            await self.uow.commit()
        return deleted
