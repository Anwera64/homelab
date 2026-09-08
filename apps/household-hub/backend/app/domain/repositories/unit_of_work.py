from typing import Protocol


class IUnitOfWork(Protocol):
    async def __aenter__(self) -> "IUnitOfWork":
        ...

    async def __aexit__(self, exc_type, exc_val, exc_tb) -> None:
        ...

    async def commit(self) -> None:
        ...

    async def rollback(self) -> None:
        ...
