import asyncio
import logging
from fastapi import FastAPI, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.database import AsyncSessionLocal
from app.domain.entities.user import User
from app.presentation.api import deps as pres_deps

logger = logging.getLogger(__name__)


# Data Sources
from app.data.datasources.user_data_source import SqliteUserDataSource
from app.data.datasources.space_data_source import SqliteSpaceDataSource
from app.data.datasources.agent_data_source import SqliteAgentDataSource
from app.data.datasources.session_data_source import SqliteSessionDataSource
from app.data.datasources.memory_data_source import SqliteMemoryDataSource
from app.data.datasources.system_setting_data_source import SqliteSystemSettingDataSource
from app.data.datasources.calendar_credential_data_source import SqliteCalendarCredentialDataSource
from app.data.datasources.document_data_source import SqliteDocumentDataSource
from app.data.datasources.gossip_data_source import SqliteGossipDataSource
from app.data.datasources.invite_data_source import SqliteInviteDataSource
from app.data.datasources.pin_reset_data_source import SqlitePinResetDataSource

# Data Mappers
from app.data.mappers.user_data_mapper import UserDataMapper
from app.data.mappers.space_data_mapper import SpaceDataMapper
from app.data.mappers.agent_data_mapper import AgentDataMapper
from app.data.mappers.session_data_mapper import SessionDataMapper
from app.data.mappers.memory_data_mapper import MemoryDataMapper
from app.data.mappers.system_setting_data_mapper import SystemSettingDataMapper
from app.data.mappers.calendar_credential_data_mapper import CalendarCredentialDataMapper
from app.data.mappers.document_data_mapper import DocumentDataMapper
from app.data.mappers.gossip_data_mapper import GossipDataMapper
from app.data.mappers.invite_data_mapper import InviteDataMapper
from app.data.mappers.pin_reset_data_mapper import PinResetDataMapper

# Repositories
from app.data.repositories.user_repository_impl import UserRepositoryImpl
from app.data.repositories.space_repository_impl import SpaceRepositoryImpl
from app.data.repositories.agent_repository_impl import AgentRepositoryImpl
from app.data.repositories.session_repository_impl import SessionRepositoryImpl
from app.data.repositories.memory_repository_impl import MemoryRepositoryImpl
from app.data.repositories.system_setting_repository_impl import SystemSettingRepositoryImpl
from app.data.repositories.calendar_credential_repository_impl import CalendarCredentialRepositoryImpl
from app.data.repositories.document_repository_impl import DocumentRepositoryImpl
from app.data.repositories.gossip_repository_impl import GossipRepositoryImpl
from app.data.repositories.invite_repository_impl import InviteRepositoryImpl
from app.data.repositories.pin_reset_repository_impl import PinResetRepositoryImpl

# Connectors
from app.data.connectors.searxng_search_connector import SearXNGSearchConnector
from app.data.connectors.pymupdf_document_reader import PyMuPDFDocumentReader
from app.data.connectors.caldav_calendar_connector import CalDavCalendarConnector
from app.data.connectors.ollama_llm_connector import OllamaLLMConnector

# Security & Persistence
from app.data.security.bcrypt_hasher import BcryptPasswordHasher
from app.data.security.jwt_token_service import JwtTokenService
from app.data.security.secret_cipher_impl import SecretCipherImpl
from app.data.persistence.unit_of_work import SqliteUnitOfWork

# Domain Use Cases
from app.domain.use_cases.auth.get_auth_status import GetAuthStatusUseCase
from app.domain.use_cases.auth.register_initial_admin import RegisterInitialAdminUseCase
from app.domain.use_cases.auth.login import LoginUseCase
from app.domain.use_cases.auth.authenticate_token import AuthenticateTokenUseCase
from app.domain.use_cases.auth.list_public_members import ListPublicMembersUseCase
from app.domain.use_cases.auth.verify_member_pin import MemberPinLocks, VerifyMemberPinUseCase
from app.domain.use_cases.auth.refresh_token import RefreshTokenUseCase
from app.domain.use_cases.auth.guard_code_guesses import CodeGuessLock, GuardCodeGuessesUseCase
from app.domain.use_cases.auth.look_up_invite import LookUpInviteUseCase
from app.domain.use_cases.auth.redeem_invite import RedeemInviteUseCase
from app.domain.use_cases.auth.redeem_pin_reset import RedeemPinResetUseCase

from app.domain.use_cases.users.list_members import ListMembersUseCase
from app.domain.use_cases.users.get_member import GetMemberUseCase
from app.domain.use_cases.users.update_profile import UpdateProfileUseCase
from app.domain.use_cases.users.change_pin import ChangePinUseCase
from app.domain.use_cases.users.deactivate_member import DeactivateMemberUseCase
from app.domain.use_cases.users.leave_household import LeaveHouseholdUseCase
from app.domain.use_cases.users.create_invite import CreateInviteUseCase
from app.domain.use_cases.users.approve_pin_reset import ApprovePinResetUseCase
from app.domain.use_cases.users.create_member import CreateMemberUseCase

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
from app.domain.use_cases.agents.purge_expired_trash_agents import PurgeExpiredTrashAgentsUseCase
from app.domain.use_cases.agents.seed_builtin_agents import SeedBuiltinAgentsUseCase

from app.domain.use_cases.sessions.list_user_sessions import ListUserSessionsUseCase
from app.domain.use_cases.sessions.get_session import GetSessionUseCase
from app.domain.use_cases.sessions.create_session import CreateSessionUseCase
from app.domain.use_cases.sessions.toggle_secret_mode import ToggleSecretModeUseCase
from app.domain.use_cases.sessions.archive_session import ArchiveSessionUseCase
from app.domain.use_cases.sessions.add_chat_message import AddChatMessageUseCase
from app.domain.use_cases.sessions.delete_session import DeleteSessionUseCase

from app.domain.use_cases.memories.list_user_memories import ListUserMemoriesUseCase
from app.domain.use_cases.memories.list_household_memories import ListHouseholdMemoriesUseCase
from app.domain.use_cases.memories.get_memory import GetMemoryUseCase
from app.domain.use_cases.memories.create_memory import CreateMemoryUseCase
from app.domain.use_cases.memories.update_memory import UpdateMemoryUseCase
from app.domain.use_cases.memories.delete_memory import DeleteMemoryUseCase

# Integration Use Cases
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

# Singletons for stateless services
_password_hasher = BcryptPasswordHasher()
_jwt_token_service = JwtTokenService(
    secret_key=settings.SECRET_KEY,
    algorithm=settings.ALGORITHM,
    expire_minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES,
)
_dummy_pin_hash = _password_hasher.hash("dummy-constant-time-pin-hash")
_pin_locks = MemberPinLocks()
_code_guess_lock = CodeGuessLock()

_user_mapper = UserDataMapper()
_space_mapper = SpaceDataMapper()
_agent_mapper = AgentDataMapper()
_session_mapper = SessionDataMapper()
_memory_mapper = MemoryDataMapper()
_system_setting_mapper = SystemSettingDataMapper()
_calendar_cred_mapper = CalendarCredentialDataMapper()
_document_mapper = DocumentDataMapper()
_gossip_mapper = GossipDataMapper()
_invite_mapper = InviteDataMapper()
_pin_reset_mapper = PinResetDataMapper()

_secret_cipher = SecretCipherImpl(secret_key=settings.SECRET_KEY)
_searxng_connector = SearXNGSearchConnector(
    base_url=settings.SEARXNG_BASE_URL,
    cache_ttl_seconds=settings.SEARCH_CACHE_TTL_SECONDS,
    max_cache_entries=settings.SEARXNG_CACHE_MAX_ENTRIES,
)
_document_reader = PyMuPDFDocumentReader()
_caldav_connector = CalDavCalendarConnector()
_ollama_connector = OllamaLLMConnector(
    base_url=settings.OLLAMA_BASE_URL,
    timeout_seconds=settings.OLLAMA_TIMEOUT_SECONDS,
)



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
    system_setting_ds = SqliteSystemSettingDataSource(session)
    calendar_cred_ds = SqliteCalendarCredentialDataSource(session)
    document_ds = SqliteDocumentDataSource(session)
    gossip_ds = SqliteGossipDataSource(session)
    invite_ds = SqliteInviteDataSource(session)
    pin_reset_ds = SqlitePinResetDataSource(session)

    user_repo = UserRepositoryImpl(user_ds, _user_mapper)
    space_repo = SpaceRepositoryImpl(space_ds, _space_mapper)
    agent_repo = AgentRepositoryImpl(agent_ds, _agent_mapper)
    session_repo = SessionRepositoryImpl(session_ds, _session_mapper)
    memory_repo = MemoryRepositoryImpl(memory_ds, _memory_mapper)
    system_setting_repo = SystemSettingRepositoryImpl(system_setting_ds, _system_setting_mapper)
    calendar_cred_repo = CalendarCredentialRepositoryImpl(calendar_cred_ds, _calendar_cred_mapper)
    document_repo = DocumentRepositoryImpl(document_ds, _document_mapper)
    gossip_repo = GossipRepositoryImpl(gossip_ds, _gossip_mapper)
    invite_repo = InviteRepositoryImpl(invite_ds, _invite_mapper)
    pin_reset_repo = PinResetRepositoryImpl(pin_reset_ds, _pin_reset_mapper)

    uow = SqliteUnitOfWork(session)

    guard_code_guesses_uc = GuardCodeGuessesUseCase(system_setting_repo, uow, _code_guess_lock)
    create_member_uc = CreateMemberUseCase(user_repo, space_repo, _password_hasher, uow)
    deactivate_member_uc = DeactivateMemberUseCase(
        user_repo,
        space_repo,
        agent_repo,
        memory_repo,
        session_repo,
        document_repo,
        calendar_cred_repo,
        uow,
    )

    context_assembler = AssembleAgentContextUseCase(
        memory_repo=memory_repo,
        gossip_repo=gossip_repo,
        user_repo=user_repo,
        max_context_tokens=settings.MAX_CONTEXT_TOKENS,
    )

    tool_lister = ListAvailableToolsUseCase()
    tool_executor = ExecuteToolUseCase(
        calendar_repo=calendar_cred_repo,
        calendar_connector=_caldav_connector,
        search_connector=_searxng_connector,
        document_repo=document_repo,
        document_reader=_document_reader,
        cipher=_secret_cipher,
        uow=uow,
        allow_calendar_delete=settings.CALENDAR_ALLOW_AGENT_DELETE,
    )

    chat_turn_uc = ProcessChatTurnUseCase(
        session_repo=session_repo,
        agent_repo=agent_repo,
        llm_client=_ollama_connector,
        context_assembler=context_assembler,
        tool_executor=tool_executor,
        tool_lister=tool_lister,
        uow=uow,
    )

    reflect_turn_uc = ReflectTurnUseCase(
        llm_client=_ollama_connector,
        memory_repo=memory_repo,
        gossip_repo=gossip_repo,
        session_repo=session_repo,
        uow=uow,
        confidence_threshold=settings.MEMORY_REFLECTION_CONFIDENCE_THRESHOLD,
        model=settings.DEFAULT_LLM_MODEL,
    )

    async def _run_background_reflection(
        session_id: str,
        user_id: str,
        username: str,
        agent_id: str,
        agent_name: str,
        user_message: str,
        assistant_message: str,
        is_secret_session: bool,
        is_turn_secret: bool,
        is_first_turn: bool,
    ):
        try:
            async with AsyncSessionLocal() as bg_sess:
                bg_container = get_container(bg_sess)
                bg_reflect_uc = bg_container[pres_deps.get_reflect_turn_use_case]
                await bg_reflect_uc.execute(
                    session_id=session_id,
                    user_id=user_id,
                    username=username,
                    agent_id=agent_id,
                    agent_name=agent_name,
                    user_message=user_message,
                    assistant_message=assistant_message,
                    is_secret_session=is_secret_session,
                    is_turn_secret=is_turn_secret,
                    is_first_turn=is_first_turn,
                )
        except Exception as exc:
            logger.error("Background reflection failed for session %s: %s", session_id, exc, exc_info=True)

    async def _run_background_chat_stream(
        session_id: str,
        current_user: User,
        content: str,
        auto_approve_writes: bool,
        queue: asyncio.Queue,
        agent_id: str = "",
        agent_name: str = "",
        is_first_turn: bool = False,
        regenerate: bool = False,
    ):
        try:
            async with AsyncSessionLocal() as bg_sess:
                bg_container = get_container(bg_sess)
                bg_stream_uc = bg_container[pres_deps.get_process_chat_turn_use_case]
                final_event = None
                # Regenerating answers the question already in the transcript, so `content` is
                # only what reflection is told about afterwards — the turn itself reads it back
                # from the conversation rather than being handed it again.
                turn = (
                    bg_stream_uc.regenerate_stream(
                        session_id=session_id,
                        current_user=current_user,
                    )
                    if regenerate
                    else bg_stream_uc.execute_stream(
                        session_id=session_id,
                        current_user=current_user,
                        content=content,
                        auto_approve_writes=auto_approve_writes,
                    )
                )
                async for event in turn:
                    if event.get("type") == "done":
                        final_event = event
                    await queue.put(event)

                if final_event:
                    bg_reflect_uc = bg_container[pres_deps.get_reflect_turn_use_case]
                    try:
                        await bg_reflect_uc.execute(
                            session_id=session_id,
                            user_id=current_user.id,
                            username=current_user.full_name,
                            agent_id=agent_id or final_event.get("agent_id", ""),
                            agent_name=agent_name or final_event.get("agent_name", ""),
                            user_message=content,
                            assistant_message=final_event.get("assistant_content", ""),
                            is_secret_session=final_event.get("is_secret", False),
                            is_turn_secret=final_event.get("is_turn_secret", False),
                            is_first_turn=is_first_turn,
                        )
                    except Exception as ref_exc:
                        logger.error("Background reflection in stream failed for session %s: %s", session_id, ref_exc, exc_info=True)
        except Exception as exc:
            logger.error("Background chat stream failed for session %s: %s", session_id, exc, exc_info=True)
            await queue.put({"type": "error", "error": str(exc)})
        finally:
            await queue.put(None)


    return {
        # Auth
        pres_deps.get_auth_status_use_case: GetAuthStatusUseCase(user_repo, system_setting_repo),
        pres_deps.get_register_initial_admin_use_case: RegisterInitialAdminUseCase(user_repo, space_repo, system_setting_repo, _password_hasher, uow),
        pres_deps.get_login_use_case: LoginUseCase(
            VerifyMemberPinUseCase(user_repo, _password_hasher, uow, _dummy_pin_hash, _pin_locks),
            _jwt_token_service,
        ),
        pres_deps.get_authenticate_token_use_case: AuthenticateTokenUseCase(user_repo, _jwt_token_service),
        pres_deps.get_list_public_members_use_case: ListPublicMembersUseCase(user_repo),
        pres_deps.get_refresh_token_use_case: RefreshTokenUseCase(_jwt_token_service),
        pres_deps.get_look_up_invite_use_case: LookUpInviteUseCase(invite_repo, user_repo, guard_code_guesses_uc),
        pres_deps.get_redeem_pin_reset_use_case: RedeemPinResetUseCase(
            pin_reset_repo, user_repo, _password_hasher, uow, _jwt_token_service, guard_code_guesses_uc
        ),
        pres_deps.get_redeem_invite_use_case: RedeemInviteUseCase(
            invite_repo, user_repo, create_member_uc, _jwt_token_service, guard_code_guesses_uc
        ),

        # Users
        pres_deps.get_list_members_use_case: ListMembersUseCase(user_repo),
        pres_deps.get_member_use_case: GetMemberUseCase(user_repo),
        pres_deps.get_update_profile_use_case: UpdateProfileUseCase(user_repo, uow),
        pres_deps.get_change_pin_use_case: ChangePinUseCase(
            VerifyMemberPinUseCase(user_repo, _password_hasher, uow, _dummy_pin_hash, _pin_locks),
            user_repo,
            _password_hasher,
            uow,
            _jwt_token_service,
        ),
        pres_deps.get_leave_household_use_case: LeaveHouseholdUseCase(
            user_repo,
            VerifyMemberPinUseCase(user_repo, _password_hasher, uow, _dummy_pin_hash, _pin_locks),
            deactivate_member_uc,
        ),
        pres_deps.get_remove_member_use_case: deactivate_member_uc,
        pres_deps.get_create_invite_use_case: CreateInviteUseCase(user_repo, invite_repo, uow),
        pres_deps.get_approve_pin_reset_use_case: ApprovePinResetUseCase(
            user_repo,
            pin_reset_repo,
            VerifyMemberPinUseCase(user_repo, _password_hasher, uow, _dummy_pin_hash, _pin_locks),
            uow,
        ),

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
        pres_deps.get_archive_session_use_case: ArchiveSessionUseCase(session_repo, uow),
        pres_deps.get_add_chat_message_use_case: AddChatMessageUseCase(session_repo, agent_repo, uow),
        pres_deps.get_delete_session_use_case: DeleteSessionUseCase(session_repo, uow),

        # Memories
        pres_deps.get_list_user_memories_use_case: ListUserMemoriesUseCase(memory_repo),
        pres_deps.get_list_household_memories_use_case: ListHouseholdMemoriesUseCase(memory_repo),
        pres_deps.get_memory_use_case: GetMemoryUseCase(memory_repo),
        pres_deps.get_create_memory_use_case: CreateMemoryUseCase(memory_repo, session_repo, agent_repo, uow),
        pres_deps.get_update_memory_use_case: UpdateMemoryUseCase(memory_repo, uow),
        pres_deps.get_delete_memory_use_case: DeleteMemoryUseCase(memory_repo, uow),

        # Integrations
        pres_deps.get_configure_calendar_use_case: ConfigureCalendarUseCase(calendar_cred_repo, _caldav_connector, _secret_cipher, uow),
        pres_deps.get_user_calendar_use_case: GetUserCalendarUseCase(calendar_cred_repo),
        pres_deps.get_delete_calendar_use_case: DeleteCalendarUseCase(calendar_cred_repo, uow),
        pres_deps.get_calendar_events_use_case: GetCalendarEventsUseCase(calendar_cred_repo, _caldav_connector, _secret_cipher),
        pres_deps.get_create_calendar_event_use_case: CreateCalendarEventUseCase(calendar_cred_repo, _caldav_connector, _secret_cipher),
        pres_deps.get_update_calendar_event_use_case: UpdateCalendarEventUseCase(calendar_cred_repo, _caldav_connector, _secret_cipher),
        pres_deps.get_delete_calendar_event_use_case: DeleteCalendarEventUseCase(calendar_cred_repo, _caldav_connector, _secret_cipher, allow_agent_delete=settings.CALENDAR_ALLOW_AGENT_DELETE),
        pres_deps.get_execute_search_use_case: ExecuteSearchUseCase(_searxng_connector),
        pres_deps.get_parse_pdf_document_use_case: ParsePdfDocumentUseCase(_document_reader, max_size_bytes=settings.MAX_PDF_SIZE_BYTES),
        pres_deps.get_save_document_use_case: SaveDocumentUseCase(document_repo, uow),
        pres_deps.get_document_use_case: GetDocumentUseCase(document_repo),
        pres_deps.get_list_documents_use_case: ListDocumentsUseCase(document_repo),
        pres_deps.get_delete_document_use_case: DeleteDocumentUseCase(document_repo, uow),
        pres_deps.get_list_available_tools_use_case: ListAvailableToolsUseCase(),
        pres_deps.get_execute_tool_use_case: ExecuteToolUseCase(
            calendar_repo=calendar_cred_repo,
            calendar_connector=_caldav_connector,
            search_connector=_searxng_connector,
            document_repo=document_repo,
            document_reader=_document_reader,
            cipher=_secret_cipher,
            uow=uow,
            allow_calendar_delete=settings.CALENDAR_ALLOW_AGENT_DELETE,
        ),

        # Gossip Bus & Stage 3 Chat
        pres_deps.get_publish_gossip_milestone_use_case: PublishGossipMilestoneUseCase(gossip_repo, uow),
        pres_deps.get_list_household_milestones_use_case: ListHouseholdMilestonesUseCase(gossip_repo),
        pres_deps.get_list_user_milestones_audit_use_case: ListUserMilestonesAuditUseCase(gossip_repo),
        pres_deps.get_revoke_gossip_milestone_use_case: RevokeGossipMilestoneUseCase(gossip_repo, uow),
        pres_deps.get_assemble_agent_context_use_case: context_assembler,
        pres_deps.get_process_chat_turn_use_case: chat_turn_uc,
        pres_deps.get_reflect_turn_use_case: reflect_turn_uc,
        pres_deps.get_background_reflection_runner: _run_background_reflection,
        pres_deps.get_background_chat_stream_runner: _run_background_chat_stream,
        pres_deps.get_llm_client: _ollama_connector,


        # Lifecycle & Background Maintenance
        SeedBuiltinAgentsUseCase: SeedBuiltinAgentsUseCase(agent_repo, uow),
        PurgeExpiredTrashAgentsUseCase: PurgeExpiredTrashAgentsUseCase(agent_repo, session_repo, uow, settings.AGENT_DELETE_GRACE_DAYS),
    }


async def get_request_container(session: AsyncSession = Depends(get_db_session)) -> dict:
    """Assembles the request-scoped dependency container once per request."""
    return get_container(session)


def setup_dependency_injection(app: FastAPI):
    """
    Wires the DI coordinator with FastAPI's request lifecycle.
    Each provider hook delegates to the request-scoped container as an async coroutine,
    ensuring no threadpool offloading and executing get_container only once per request.
    """
    # Create wrapper factory for each stub that resolves via request container
    for stub_fn in [
        pres_deps.get_auth_status_use_case,
        pres_deps.get_register_initial_admin_use_case,
        pres_deps.get_login_use_case,
        pres_deps.get_authenticate_token_use_case,
        pres_deps.get_list_public_members_use_case,
        pres_deps.get_refresh_token_use_case,
        pres_deps.get_look_up_invite_use_case,
        pres_deps.get_redeem_invite_use_case,
        pres_deps.get_approve_pin_reset_use_case,
        pres_deps.get_redeem_pin_reset_use_case,
        pres_deps.get_list_members_use_case,
        pres_deps.get_member_use_case,
        pres_deps.get_update_profile_use_case,
        pres_deps.get_change_pin_use_case,
        pres_deps.get_remove_member_use_case,
        pres_deps.get_leave_household_use_case,
        pres_deps.get_create_invite_use_case,
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
        pres_deps.get_archive_session_use_case,
        pres_deps.get_add_chat_message_use_case,
        pres_deps.get_delete_session_use_case,
        pres_deps.get_list_user_memories_use_case,
        pres_deps.get_list_household_memories_use_case,
        pres_deps.get_memory_use_case,
        pres_deps.get_create_memory_use_case,
        pres_deps.get_update_memory_use_case,
        pres_deps.get_delete_memory_use_case,
        pres_deps.get_configure_calendar_use_case,
        pres_deps.get_user_calendar_use_case,
        pres_deps.get_delete_calendar_use_case,
        pres_deps.get_calendar_events_use_case,
        pres_deps.get_create_calendar_event_use_case,
        pres_deps.get_update_calendar_event_use_case,
        pres_deps.get_delete_calendar_event_use_case,
        pres_deps.get_execute_search_use_case,
        pres_deps.get_parse_pdf_document_use_case,
        pres_deps.get_save_document_use_case,
        pres_deps.get_document_use_case,
        pres_deps.get_list_documents_use_case,
        pres_deps.get_delete_document_use_case,
        pres_deps.get_list_available_tools_use_case,
        pres_deps.get_execute_tool_use_case,
        pres_deps.get_publish_gossip_milestone_use_case,
        pres_deps.get_list_household_milestones_use_case,
        pres_deps.get_list_user_milestones_audit_use_case,
        pres_deps.get_revoke_gossip_milestone_use_case,
        pres_deps.get_assemble_agent_context_use_case,
        pres_deps.get_process_chat_turn_use_case,
        pres_deps.get_reflect_turn_use_case,
        pres_deps.get_background_reflection_runner,
        pres_deps.get_background_chat_stream_runner,
        pres_deps.get_llm_client,
    ]:

        def make_provider(target_stub):
            async def provider(container: dict = Depends(get_request_container)):
                return container[target_stub]
            return provider

        app.dependency_overrides[stub_fn] = make_provider(stub_fn)

