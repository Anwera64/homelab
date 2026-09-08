from typing import Optional
from fastapi import Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer

from app.domain.entities.user import User
from app.domain.exceptions import DomainException, EntityNotFoundException

# Use Cases
from app.domain.use_cases.auth.get_auth_status import GetAuthStatusUseCase
from app.domain.use_cases.auth.register_initial_admin import RegisterInitialAdminUseCase
from app.domain.use_cases.auth.login import LoginUseCase
from app.domain.use_cases.auth.authenticate_token import AuthenticateTokenUseCase

from app.domain.use_cases.users.list_members import ListMembersUseCase
from app.domain.use_cases.users.get_member import GetMemberUseCase
from app.domain.use_cases.users.create_member import CreateMemberUseCase
from app.domain.use_cases.users.update_profile import UpdateProfileUseCase
from app.domain.use_cases.users.delete_member import DeleteMemberUseCase

from app.domain.use_cases.spaces.get_shared_space import GetSharedSpaceUseCase
from app.domain.use_cases.spaces.get_personal_space import GetPersonalSpaceUseCase
from app.domain.use_cases.spaces.get_space_by_id import GetSpaceByIdUseCase
from app.domain.use_cases.spaces.update_personal_settings import UpdatePersonalSettingsUseCase
from app.domain.use_cases.spaces.update_shared_settings import UpdateSharedSettingsUseCase
from app.domain.use_cases.spaces.update_space_settings import UpdateSpaceSettingsUseCase

from app.domain.use_cases.agents.list_agents import ListAgentsUseCase
from app.domain.use_cases.agents.get_agent import GetAgentUseCase
from app.domain.use_cases.agents.create_agent import CreateAgentUseCase
from app.domain.use_cases.agents.update_agent import UpdateAgentUseCase
from app.domain.use_cases.agents.soft_delete_agent import SoftDeleteAgentUseCase
from app.domain.use_cases.agents.restore_agent import RestoreAgentUseCase
from app.domain.use_cases.agents.list_trash_agents import ListTrashAgentsUseCase
from app.domain.use_cases.agents.purge_trash_agent import PurgeTrashAgentUseCase

from app.domain.use_cases.sessions.list_user_sessions import ListUserSessionsUseCase
from app.domain.use_cases.sessions.get_session import GetSessionUseCase
from app.domain.use_cases.sessions.create_session import CreateSessionUseCase
from app.domain.use_cases.sessions.toggle_secret_mode import ToggleSecretModeUseCase
from app.domain.use_cases.sessions.add_chat_message import AddChatMessageUseCase
from app.domain.use_cases.sessions.delete_session import DeleteSessionUseCase

from app.domain.use_cases.memories.list_user_memories import ListUserMemoriesUseCase
from app.domain.use_cases.memories.list_household_memories import ListHouseholdMemoriesUseCase
from app.domain.use_cases.memories.get_memory import GetMemoryUseCase
from app.domain.use_cases.memories.create_memory import CreateMemoryUseCase
from app.domain.use_cases.memories.update_memory import UpdateMemoryUseCase
from app.domain.use_cases.memories.delete_memory import DeleteMemoryUseCase

from app.domain.use_cases.integrations.configure_calendar import ConfigureCalendarUseCase
from app.domain.use_cases.integrations.get_user_calendar import GetUserCalendarUseCase
from app.domain.use_cases.integrations.delete_calendar import DeleteCalendarUseCase
from app.domain.use_cases.integrations.get_calendar_events import GetCalendarEventsUseCase
from app.domain.use_cases.integrations.create_calendar_event import CreateCalendarEventUseCase
from app.domain.use_cases.integrations.update_calendar_event import UpdateCalendarEventUseCase
from app.domain.use_cases.integrations.delete_calendar_event import DeleteCalendarEventUseCase
from app.domain.use_cases.integrations.execute_search import ExecuteSearchUseCase
from app.domain.use_cases.integrations.parse_pdf_document import ParsePdfDocumentUseCase
from app.domain.use_cases.integrations.manage_documents import (
    SaveDocumentUseCase,
    GetDocumentUseCase,
    ListDocumentsUseCase,
    DeleteDocumentUseCase,
)
from app.domain.use_cases.integrations.list_available_tools import ListAvailableToolsUseCase
from app.domain.use_cases.integrations.execute_tool import ExecuteToolUseCase
from app.domain.use_cases.gossip.publish_gossip_milestone import PublishGossipMilestoneUseCase
from app.domain.use_cases.gossip.manage_gossip_milestones import (
    ListHouseholdMilestonesUseCase,
    ListUserMilestonesAuditUseCase,
    RevokeGossipMilestoneUseCase,
)
from app.domain.use_cases.chat.assemble_agent_context import AssembleAgentContextUseCase
from app.domain.use_cases.chat.process_chat_turn import ProcessChatTurnUseCase
from app.domain.use_cases.memories.reflect_turn import ReflectTurnUseCase
from app.domain.repositories.llm_client import ILLMClient
from app.presentation.api.session_lock import SessionLockRegistry


reusable_oauth2 = OAuth2PasswordBearer(
    tokenUrl="/api/v1/auth/login",
    auto_error=False,
)


# =========================================================================
# Dependency Injection Provider Stubs (Overridden by Bootstrap Coordinator)
# =========================================================================
def get_auth_status_use_case() -> GetAuthStatusUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_register_initial_admin_use_case() -> RegisterInitialAdminUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_login_use_case() -> LoginUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_authenticate_token_use_case() -> AuthenticateTokenUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_list_members_use_case() -> ListMembersUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_member_use_case() -> GetMemberUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_create_member_use_case() -> CreateMemberUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_update_profile_use_case() -> UpdateProfileUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_delete_member_use_case() -> DeleteMemberUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_shared_space_use_case() -> GetSharedSpaceUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_personal_space_use_case() -> GetPersonalSpaceUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_space_by_id_use_case() -> GetSpaceByIdUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_update_personal_settings_use_case() -> UpdatePersonalSettingsUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_update_shared_settings_use_case() -> UpdateSharedSettingsUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_update_space_settings_use_case() -> UpdateSpaceSettingsUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_list_agents_use_case() -> ListAgentsUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_agent_use_case() -> GetAgentUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_create_agent_use_case() -> CreateAgentUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_update_agent_use_case() -> UpdateAgentUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_soft_delete_agent_use_case() -> SoftDeleteAgentUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_restore_agent_use_case() -> RestoreAgentUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_list_trash_agents_use_case() -> ListTrashAgentsUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_purge_trash_agent_use_case() -> PurgeTrashAgentUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_list_user_sessions_use_case() -> ListUserSessionsUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_session_use_case() -> GetSessionUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_create_session_use_case() -> CreateSessionUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_toggle_secret_mode_use_case() -> ToggleSecretModeUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_add_chat_message_use_case() -> AddChatMessageUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_delete_session_use_case() -> DeleteSessionUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_list_user_memories_use_case() -> ListUserMemoriesUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_list_household_memories_use_case() -> ListHouseholdMemoriesUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_memory_use_case() -> GetMemoryUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_create_memory_use_case() -> CreateMemoryUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_update_memory_use_case() -> UpdateMemoryUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_delete_memory_use_case() -> DeleteMemoryUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_configure_calendar_use_case() -> ConfigureCalendarUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_user_calendar_use_case() -> GetUserCalendarUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_delete_calendar_use_case() -> DeleteCalendarUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_calendar_events_use_case() -> GetCalendarEventsUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_create_calendar_event_use_case() -> CreateCalendarEventUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_update_calendar_event_use_case() -> UpdateCalendarEventUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_delete_calendar_event_use_case() -> DeleteCalendarEventUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_execute_search_use_case() -> ExecuteSearchUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_parse_pdf_document_use_case() -> ParsePdfDocumentUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_save_document_use_case() -> SaveDocumentUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_document_use_case() -> GetDocumentUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_list_documents_use_case() -> ListDocumentsUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_delete_document_use_case() -> DeleteDocumentUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_list_available_tools_use_case() -> ListAvailableToolsUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_execute_tool_use_case() -> ExecuteToolUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

_session_lock_registry = SessionLockRegistry()

def get_session_lock_registry() -> SessionLockRegistry:
    return _session_lock_registry

def get_publish_gossip_milestone_use_case() -> PublishGossipMilestoneUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_list_household_milestones_use_case() -> ListHouseholdMilestonesUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_list_user_milestones_audit_use_case() -> ListUserMilestonesAuditUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_revoke_gossip_milestone_use_case() -> RevokeGossipMilestoneUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_assemble_agent_context_use_case() -> AssembleAgentContextUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_process_chat_turn_use_case() -> ProcessChatTurnUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_reflect_turn_use_case() -> ReflectTurnUseCase:
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_background_reflection_runner():
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_background_chat_stream_runner():
    raise NotImplementedError("Wired by bootstrap coordinator")

def get_llm_client() -> ILLMClient:
    raise NotImplementedError("Wired by bootstrap coordinator")





# =========================================================================
# Presentation Auth Providers
# =========================================================================
async def get_current_user(
    token: Optional[str] = Depends(reusable_oauth2),
    auth_use_case: AuthenticateTokenUseCase = Depends(get_authenticate_token_use_case),
) -> User:
    if not token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication required.",
            headers={"WWW-Authenticate": "Bearer"},
        )
    try:
        return await auth_use_case.execute(token)
    except (DomainException, Exception):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired token.",
            headers={"WWW-Authenticate": "Bearer"},
        )


async def get_current_admin_user(
    current_user: User = Depends(get_current_user),
) -> User:
    if not current_user.is_admin:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="The requested action requires administrator privileges.",
        )
    return current_user
