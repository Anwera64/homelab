from sqlalchemy.ext.asyncio import AsyncSession
from app.domain.repositories.unit_of_work import IUnitOfWork


class SqliteUnitOfWork(IUnitOfWork):
    def __init__(self, session: AsyncSession):
        self.session = session

    async def __aenter__(self) -> "SqliteUnitOfWork":
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb) -> None:
        if exc_type:
            await self.rollback()

    async def commit(self) -> None:
        await self.session.commit()

    async def rollback(self) -> None:
        await self.session.rollback()
