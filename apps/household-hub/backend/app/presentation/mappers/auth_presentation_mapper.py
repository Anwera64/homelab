from typing import Dict, Any
from app.domain.entities.user import User
from app.presentation.schemas.auth_schemas import Token, AuthStatus
from app.presentation.mappers.user_presentation_mapper import UserPresentationMapper


class AuthPresentationMapper:
    @staticmethod
    def to_token_response(token_data: Dict[str, Any]) -> Token:
        return Token(
            access_token=token_data["access_token"],
            token_type=token_data.get("token_type", "bearer"),
            user=UserPresentationMapper.to_response(token_data["user"]),
        )

    @staticmethod
    def to_status_response(status_data: Dict[str, Any]) -> AuthStatus:
        return AuthStatus(
            is_initialized=status_data["is_initialized"],
            member_count=status_data["member_count"],
        )
