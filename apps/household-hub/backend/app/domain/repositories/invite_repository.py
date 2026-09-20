from datetime import datetime
from typing import Optional, Protocol

from app.domain.entities.invite import Invite


class IInviteRepository(Protocol):
    async def create(self, invite: Invite) -> Invite:
        ...

    async def get_by_code(self, code: str) -> Optional[Invite]:
        ...

    async def delete_unused_for_name(self, invited_name: str) -> None:
        """Retires every unused invite for that name, ignoring case."""
        ...

    async def claim(self, invite_id: str, used_at: datetime) -> bool:
        """
        Marks the invite used only if nobody has yet, in one conditional write.
        True if this call claimed it; False if it was already used.
        """
        ...
