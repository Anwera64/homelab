from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Optional


@dataclass
class SystemSetting:
    key: str
    value: str
    created_at: Optional[datetime] = None
    updated_at: Optional[datetime] = None
