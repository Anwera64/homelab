from app.domain.repositories.user_repository import IUserRepository


class GetAuthStatusUseCase:
    def __init__(self, user_repo: IUserRepository):
        self.user_repo = user_repo

    async def execute(self) -> dict:
        count = await self.user_repo.count()
        return {"is_initialized": count > 0, "member_count": count}
