from pydantic import BaseModel

from app.presentation.schemas.auth_schemas import Pin


class ChangePinRequest(BaseModel):
    current_pin: Pin
    new_pin: Pin
