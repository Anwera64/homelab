from dataclasses import dataclass, field
from datetime import datetime, timezone
import uuid


def get_utc_now() -> datetime:
    return datetime.now(timezone.utc)


@dataclass
class LLMModel:
    """A model the household can talk to. Only `provider_model` is the inference server's name for it."""

    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    provider_model: str = ""
    display_name: str = ""
    is_default: bool = False
    created_at: datetime = field(default_factory=get_utc_now)
    updated_at: datetime = field(default_factory=get_utc_now)
