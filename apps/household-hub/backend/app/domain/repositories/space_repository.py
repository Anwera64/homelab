from typing import Protocol, Optional
from app.domain.entities.space import Space


class ISpaceRepository(Protocol):
    async def get_shared(self) -> Optional[Space]:
        ...

    async def get_by_id(self, space_id: str) -> Optional[Space]:
        ...

    async def get_by_owner_id(self, owner_id: str) -> Optional[Space]:
        ...

    async def create(self, space: Space) -> Space:
        ...

    async def update(self, space: Space) -> Space:
        ...

    async def delete_by_owner_id(self, owner_id: str) -> None:
        ...
