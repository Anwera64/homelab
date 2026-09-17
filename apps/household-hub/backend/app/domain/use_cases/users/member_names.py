from app.domain.exceptions import NameTakenException
from app.domain.repositories.user_repository import IUserRepository


async def raise_if_name_taken(user_repo: IUserRepository, full_name: str) -> None:
    """
    The profile picker tells members apart by name alone, however it's capitalised. Only active
    members count: a past member's name is free again.
    """
    active = await user_repo.list_active()
    if any(m.full_name.casefold() == full_name.casefold() for m in active):
        raise NameTakenException("Someone in the household already has that name.")
