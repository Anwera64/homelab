from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any, Dict
import uuid


def get_utc_now() -> datetime:
    return datetime.now(timezone.utc)


DEFAULT_SHARED_SETTINGS: Dict[str, Any] = {
    "layout_version": 1,
    "columns": 4,
    "widgets": [
        {
            "id": "w-shared-cal",
            "type": "calendar",
            "title": "Household Schedule",
            "size": "large",
            "position": 0,
            "config": {"view": "week", "days_ahead": 7},
        },
        {
            "id": "w-shared-launcher",
            "type": "agent_launcher",
            "title": "Household AI Assistants",
            "size": "medium",
            "position": 1,
            "config": {"pinned_agents": ["assistant", "researcher"]},
        },
    ],
}

DEFAULT_PERSONAL_SETTINGS: Dict[str, Any] = {
    "layout_version": 1,
    "columns": 2,
    "widgets": [
        {
            "id": "w-personal-agenda",
            "type": "agenda",
            "title": "My Agenda",
            "size": "medium",
            "position": 0,
            "config": {"days_ahead": 3},
        },
        {
            "id": "w-personal-recent",
            "type": "recent_sessions",
            "title": "Recent Chats",
            "size": "medium",
            "position": 1,
            "config": {"limit": 5},
        },
    ],
}


@dataclass
class Space:
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    name: str = ""
    type: str = "personal"  # "personal" or "shared"
    owner_id: str | None = None
    settings: Dict[str, Any] = field(default_factory=dict)
    created_at: datetime = field(default_factory=get_utc_now)
    updated_at: datetime = field(default_factory=get_utc_now)

    @property
    def is_personal(self) -> bool:
        return self.type == "personal"

    @property
    def is_shared(self) -> bool:
        return self.type == "shared"

    def can_access(self, user_id: str) -> bool:
        """
        Zero-Leak Rule:
        Shared spaces are accessible to all household members.
        Personal spaces are strictly accessible ONLY to their owner.
        """
        if self.is_shared:
            return True
        return self.owner_id == user_id
