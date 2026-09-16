from pydantic import BaseModel

from app.presentation.schemas.auth_schemas import Pin


class ChangePinRequest(BaseModel):
    current_pin: Pin
    new_pin: Pin


class LeaveHouseholdRequest(BaseModel):
    """Your own PIN: deleting your account is the one thing only you can do."""
    pin: Pin
