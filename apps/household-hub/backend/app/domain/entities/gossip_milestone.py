from dataclasses import dataclass, field
from datetime import datetime, timezone
import re
from typing import Any, Dict, Optional
import uuid


def get_utc_now() -> datetime:
    return datetime.now(timezone.utc)


@dataclass
class GossipMilestone:
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    source_user_id: str = ""
    source_username: str = ""
    reporting_agent_id: Optional[str] = None
    reporting_agent_name: str = ""
    target_scope: str = "household"  # "household" or target user_id
    category: str = "milestone"  # "milestone", "schedule_constraint", "academic_deadline", "dietary_preference", "project_update"
    summary: str = ""
    details_json: Dict[str, Any] = field(default_factory=dict)
    expires_at: Optional[datetime] = None
    source_session_id: Optional[str] = None
    is_active: bool = True
    created_at: datetime = field(default_factory=get_utc_now)
    updated_at: datetime = field(default_factory=get_utc_now)

    def is_expired(self, now: Optional[datetime] = None) -> bool:
        if not self.expires_at:
            return False
        current_time = now or get_utc_now()
        exp = self.expires_at if self.expires_at.tzinfo else self.expires_at.replace(tzinfo=timezone.utc)
        curr = current_time if current_time.tzinfo else current_time.replace(tzinfo=timezone.utc)
        return curr >= exp

    def revoke(self) -> None:
        self.is_active = False
        self.updated_at = get_utc_now()

    def sanitize(self, max_length: int = 250) -> str:
        text = self.summary or ""
        # Strip injection delimiters
        patterns = [
            r"(?i)\bsystem\s*:",
            r"(?i)\bhuman\s*:",
            r"(?i)\bassistant\s*:",
            r"(?i)<\|.*?\|>",
            r"###",
            r"---",
        ]
        for pattern in patterns:
            text = re.sub(pattern, " ", text)

        # Normalize whitespace
        text = re.sub(r"\s+", " ", text).strip()
        # Truncate
        if len(text) > max_length:
            text = text[:max_length]
        return text
