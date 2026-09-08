from fastapi import FastAPI, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.database import AsyncSessionLocal
from app.presentation.api import deps as pres_deps

# Data Sources
from app.data.datasources.user_data_source import SqliteUserDataSource
from app.data.datasources.space_data_source import SqliteSpaceDataSource
from app.data.datasources.agent_data_source import SqliteAgentDataSource
from app.data.datasources.session_data_source import SqliteSessionDataSource
from app.data.datasources.memory_data_source import SqliteMemoryDataSource

# Data Mappers
from app.data.mappers.user_data_mapper import UserDataMapper
from app.data.mappers.space_data_mapper import SpaceDataMapper
from app.data.mappers.agent_data_mapper import AgentDataMapper
from app.data.mappers.session_data_mapper import SessionDataMapper
from app.data.mappers.memory_data_mapper import MemoryDataMapper

# Repositories
from app.data.repositories.user_repository_impl import UserRepositoryImpl
from app.data.repositories.space_repository_impl import SpaceRepositoryImpl
from app.data.repositories.agent_repository_impl import AgentRepositoryImpl
from app.data.repositories.session_repository_impl import SessionRepositoryImpl
from app.data.repositories.memory_repository_impl import MemoryRepositoryImpl

# Security & Persistence
from app.data.security.bcrypt_hasher import BcryptPasswordHasher
from app.data.security.jwt_token_service import JwtTokenService
from app.data.persistence.unit_of_work import SqliteUnitOfWork

# Domain Use Cases
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
from app.domain.use_cases.agents.seed_builtin_agents import SeedBuiltinAgentsUseCase

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

# Singletons for stateless services
_password_hasher = BcryptPasswordHasher()
_jwt_token_service = JwtTokenService(
    secret_key=settings.SECRET_KEY,
    algorithm=settings.ALGORITHM,
    expire_minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES,
)
_dummy_password_hash = _password_hasher.hash("dummy-constant-time-password-hash")

_user_mapper = UserDataMapper()
_space_mapper = SpaceDataMapper()
_agent_mapper = AgentDataMapper()
_session_mapper = SessionDataMapper()
_memory_mapper = MemoryDataMapper()


async def get_db_session():
    async with AsyncSessionLocal() as session:
        yield session


def get_container(session: AsyncSession):
    """Assembles all repositories, unit of work, and use cases for a given DB session."""
    user_ds = SqliteUserDataSource(session)
    space_ds = SqliteSpaceDataSource(session)
    agent_ds = SqliteAgentDataSource(session)
    session_ds = SqliteSessionDataSource(session)
    memory_ds = SqliteMemoryDataSource(session)

    user_repo = UserRepositoryImpl(user_ds, _user_mapper)
    space_repo = SpaceRepositoryImpl(space_ds, _space_mapper)
    agent_repo = AgentRepositoryImpl(agent_ds, _agent_mapper)
    session_repo = SessionRepositoryImpl(session_ds, _session_mapper)
    memory_repo = MemoryRepositoryImpl(memory_ds, _memory_mapper)

    uow = SqliteUnitOfWork(session)

    return {
        # Auth
        pres_deps.get_auth_status_use_case: GetAuthStatusUseCase(user_repo),
        pres_deps.get_register_initial_admin_use_case: RegisterInitialAdminUseCase(user_repo, space_repo, _password_hasher, uow),
        pres_deps.get_login_use_case: LoginUseCase(user_repo, _password_hasher, _jwt_token_service, _dummy_password_hash),
        pres_deps.get_authenticate_token_use_case: AuthenticateTokenUseCase(user_repo, _jwt_token_service),

        # Users
        pres_deps.get_list_members_use_case: ListMembersUseCase(user_repo),
        pres_deps.get_member_use_case: GetMemberUseCase(user_repo),
        pres_deps.get_create_member_use_case: CreateMemberUseCase(user_repo, space_repo, _password_hasher, uow),
        pres_deps.get_update_profile_use_case: UpdateProfileUseCase(user_repo, _password_hasher, uow),
        pres_deps.get_delete_member_use_case: DeleteMemberUseCase(user_repo, space_repo, agent_repo, memory_repo, uow),

        # Spaces
        pres_deps.get_shared_space_use_case: GetSharedSpaceUseCase(space_repo, uow),
        pres_deps.get_personal_space_use_case: GetPersonalSpaceUseCase(space_repo),
        pres_deps.get_space_by_id_use_case: GetSpaceByIdUseCase(space_repo),
        pres_deps.get_update_personal_settings_use_case: UpdatePersonalSettingsUseCase(space_repo, uow),
        pres_deps.get_update_shared_settings_use_case: UpdateSharedSettingsUseCase(space_repo, uow),
        pres_deps.get_update_space_settings_use_case: UpdateSpaceSettingsUseCase(space_repo, uow),

        # Agents
        pres_deps.get_list_agents_use_case: ListAgentsUseCase(agent_repo),
        pres_deps.get_agent_use_case: GetAgentUseCase(agent_repo),
        pres_deps.get_create_agent_use_case: CreateAgentUseCase(agent_repo, session_repo, uow, settings.AGENT_DELETE_GRACE_DAYS),
        pres_deps.get_update_agent_use_case: UpdateAgentUseCase(agent_repo, uow),
        pres_deps.get_soft_delete_agent_use_case: SoftDeleteAgentUseCase(agent_repo, uow),
        pres_deps.get_restore_agent_use_case: RestoreAgentUseCase(agent_repo, uow, settings.AGENT_DELETE_GRACE_DAYS),
        pres_deps.get_list_trash_agents_use_case: ListTrashAgentsUseCase(agent_repo, settings.AGENT_DELETE_GRACE_DAYS),
        pres_deps.get_purge_trash_agent_use_case: PurgeTrashAgentUseCase(agent_repo, session_repo, uow),

        # Sessions
        pres_deps.get_list_user_sessions_use_case: ListUserSessionsUseCase(session_repo),
        pres_deps.get_session_use_case: GetSessionUseCase(session_repo),
        pres_deps.get_create_session_use_case: CreateSessionUseCase(session_repo, agent_repo, uow),
        pres_deps.get_toggle_secret_mode_use_case: ToggleSecretModeUseCase(session_repo, uow),
        pres_deps.get_add_chat_message_use_case: AddChatMessageUseCase(session_repo, agent_repo, uow),
        pres_deps.get_delete_session_use_case: DeleteSessionUseCase(session_repo, uow),

        # Memories
        pres_deps.get_list_user_memories_use_case: ListUserMemoriesUseCase(memory_repo),
        pres_deps.get_list_household_memories_use_case: ListHouseholdMemoriesUseCase(memory_repo),
        pres_deps.get_memory_use_case: GetMemoryUseCase(memory_repo),
        pres_deps.get_create_memory_use_case: CreateMemoryUseCase(memory_repo, session_repo, agent_repo, uow),
        pres_deps.get_update_memory_use_case: UpdateMemoryUseCase(memory_repo, uow),
        pres_deps.get_delete_memory_use_case: DeleteMemoryUseCase(memory_repo, uow),
    }


def setup_dependency_injection(app: FastAPI):
    """
    Wires the DI coordinator with FastAPI's request lifecycle.
    Each provider hook delegates to the request-scoped container.
    """
    # Create wrapper factory for each stub that resolves via request session
    for stub_fn in [
        pres_deps.get_auth_status_use_case,
        pres_deps.get_register_initial_admin_use_case,
        pres_deps.get_login_use_case,
        pres_deps.get_authenticate_token_use_case,
        pres_deps.get_list_members_use_case,
        pres_deps.get_member_use_case,
        pres_deps.get_create_member_use_case,
        pres_deps.get_update_profile_use_case,
        pres_deps.get_delete_member_use_case,
        pres_deps.get_shared_space_use_case,
        pres_deps.get_personal_space_use_case,
        pres_deps.get_space_by_id_use_case,
        pres_deps.get_update_personal_settings_use_case,
        pres_deps.get_update_shared_settings_use_case,
        pres_deps.get_update_space_settings_use_case,
        pres_deps.get_list_agents_use_case,
        pres_deps.get_agent_use_case,
        pres_deps.get_create_agent_use_case,
        pres_deps.get_update_agent_use_case,
        pres_deps.get_soft_delete_agent_use_case,
        pres_deps.get_restore_agent_use_case,
        pres_deps.get_list_trash_agents_use_case,
        pres_deps.get_purge_trash_agent_use_case,
        pres_deps.get_list_user_sessions_use_case,
        pres_deps.get_session_use_case,
        pres_deps.get_create_session_use_case,
        pres_deps.get_toggle_secret_mode_use_case,
        pres_deps.get_add_chat_message_use_case,
        pres_deps.get_delete_session_use_case,
        pres_deps.get_list_user_memories_use_case,
        pres_deps.get_list_household_memories_use_case,
        pres_deps.get_memory_use_case,
        pres_deps.get_create_memory_use_case,
        pres_deps.get_update_memory_use_case,
        pres_deps.get_delete_memory_use_case,
    ]:
        def make_provider(target_stub):
            def provider(session: AsyncSession = Depends(get_db_session)):
                container = get_container(session)
                return container[target_stub]
            return provider

        app.dependency_overrides[stub_fn] = make_provider(stub_fn)
