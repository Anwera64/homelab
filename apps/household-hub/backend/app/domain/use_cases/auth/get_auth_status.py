from app.domain.repositories.user_repository import IUserRepository
from app.domain.repositories.system_setting_repository import ISystemSettingRepository


class GetAuthStatusUseCase:
    def __init__(
        self,
        user_repo: IUserRepository,
        system_setting_repo: ISystemSettingRepository,
    ):
        self.user_repo = user_repo
        self.system_setting_repo = system_setting_repo

    async def execute(self) -> dict:
        count = await self.user_repo.count()
        admin_setting = await self.system_setting_repo.get("initial_admin_id")
        is_initialized = bool(admin_setting and admin_setting.value != "pending") or (count > 0)
        return {"is_initialized": is_initialized, "member_count": count}
