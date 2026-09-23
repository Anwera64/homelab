from dataclasses import dataclass, field
from datetime import datetime, timezone, timedelta
from typing import List
import uuid


def get_utc_now() -> datetime:
    return datetime.now(timezone.utc)


@dataclass
class AgentPersonality:
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    slug: str = ""
    name: str = ""
    description: str = ""
    avatar: str = "🤖"
    system_prompt: str = ""
    llm_model_id: str | None = None
    temperature: float = 0.7
    top_p: float = 0.9
    tool_permissions: List[str] = field(default_factory=list)
    owner_id: str | None = None
    is_builtin: bool = False
    is_active: bool = True
    deleted_at: datetime | None = None
    created_at: datetime = field(default_factory=get_utc_now)
    updated_at: datetime = field(default_factory=get_utc_now)

    @property
    def is_in_trash(self) -> bool:
        return self.deleted_at is not None

    def days_remaining_in_grace_period(self, now: datetime, grace_days: int = 7) -> int:
        if not self.deleted_at:
            return 0
        del_at = self.deleted_at if self.deleted_at.tzinfo else self.deleted_at.replace(tzinfo=timezone.utc)
        now_utc = now if now.tzinfo else now.replace(tzinfo=timezone.utc)
        days_elapsed = (now_utc - del_at).days
        return max(0, grace_days - days_elapsed)

    def can_restore(self, now: datetime, grace_days: int = 7) -> bool:
        if not self.deleted_at:
            return False
        del_at = self.deleted_at if self.deleted_at.tzinfo else self.deleted_at.replace(tzinfo=timezone.utc)
        now_utc = now if now.tzinfo else now.replace(tzinfo=timezone.utc)
        days_elapsed = (now_utc - del_at).days
        return days_elapsed <= grace_days
