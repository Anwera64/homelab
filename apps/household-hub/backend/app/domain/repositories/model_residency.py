from datetime import datetime
from typing import Optional, Protocol


class IModelResidency(Protocol):
    """Whether the inference server has a model in memory now, and until when it keeps it."""

    async def loaded_until(self, model: str) -> Optional[datetime]:
        """The time the loaded model will be unloaded, in UTC; None when it isn't loaded or can't be asked."""
        ...
