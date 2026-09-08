from datetime import datetime
from typing import Optional, Dict, Any
from pydantic import BaseModel, ConfigDict


class SpaceRead(BaseModel):
    id: str
    name: str
    type: str
    owner_id: Optional[str] = None
    settings: Dict[str, Any] = {}
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)


class SpaceSettingsUpdate(BaseModel):
    settings: Dict[str, Any]
