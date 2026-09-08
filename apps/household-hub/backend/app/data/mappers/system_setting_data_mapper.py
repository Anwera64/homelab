from app.domain.entities.system_setting import SystemSetting
from app.data.models.system_setting_model import SystemSettingModel


class SystemSettingDataMapper:
    @staticmethod
    def to_domain(model: SystemSettingModel) -> SystemSetting:
        return SystemSetting(
            key=model.key,
            value=model.value,
            created_at=model.created_at,
            updated_at=model.updated_at,
        )

    @staticmethod
    def to_model(entity: SystemSetting) -> SystemSettingModel:
        return SystemSettingModel(
            key=entity.key,
            value=entity.value,
            created_at=entity.created_at,
            updated_at=entity.updated_at,
        )
