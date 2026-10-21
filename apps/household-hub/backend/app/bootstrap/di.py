import asyncio
import logging
from datetime import timedelta
from typing import Any, Dict, Optional
from fastapi import FastAPI, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.database import AsyncSessionLocal
from app.domain.entities.user import User
from app.presentation.api import deps as pres_deps
from app.bootstrap.after_turn import AfterTurnWork
from app.bootstrap.turn_activity import HubTurnActivity

logger = logging.getLogger(__name__)


# Data Sources
from app.data.datasources.user_data_source import SqliteUserDataSource
from app.data.datasources.space_data_source import SqliteSpaceDataSource
from app.data.datasources.agent_data_source import SqliteAgentDataSource
from app.data.datasources.session_data_source import SqliteSessionDataSource
from app.data.datasources.memory_data_source import SqliteMemoryDataSource
from app.data.datasources.system_setting_data_source import SqliteSystemSettingDataSource
from app.data.datasources.llm_model_data_source import SqliteLLMModelDataSource
from app.data.datasources.calendar_credential_data_source import SqliteCalendarCredentialDataSource
from app.data.datasources.calendar_source_data_source import SqliteCalendarSourceDataSource
from app.data.datasources.tool_approval_data_source import SqliteToolApprovalDataSource
from app.data.datasources.document_data_source import SqliteDocumentDataSource
from app.data.datasources.invite_data_source import SqliteInviteDataSource
from app.data.datasources.pin_reset_data_source import SqlitePinResetDataSource
from app.data.datasources.device_session_data_source import SqliteDeviceSessionDataSource

# Data Mappers
from app.data.mappers.user_data_mapper import UserDataMapper
from app.data.mappers.space_data_mapper import SpaceDataMapper
from app.data.mappers.agent_data_mapper import AgentDataMapper
from app.data.mappers.session_data_mapper import SessionDataMapper
from app.data.mappers.memory_data_mapper import MemoryDataMapper
from app.data.mappers.system_setting_data_mapper import SystemSettingDataMapper
from app.data.mappers.llm_model_data_mapper import LLMModelDataMapper
from app.data.mappers.calendar_credential_data_mapper import CalendarCredentialDataMapper
from app.data.mappers.calendar_source_data_mapper import CalendarSourceDataMapper
from app.data.mappers.tool_approval_data_mapper import ToolApprovalDataMapper
from app.data.mappers.document_data_mapper import DocumentDataMapper
from app.data.mappers.invite_data_mapper import InviteDataMapper
from app.data.mappers.pin_reset_data_mapper import PinResetDataMapper
from app.data.mappers.device_session_data_mapper import DeviceSessionDataMapper

# Repositories
from app.data.repositories.user_repository_impl import UserRepositoryImpl
from app.data.repositories.space_repository_impl import SpaceRepositoryImpl
from app.data.repositories.agent_repository_impl import AgentRepositoryImpl
from app.data.repositories.session_repository_impl import SessionRepositoryImpl
from app.data.repositories.memory_repository_impl import MemoryRepositoryImpl
from app.data.repositories.system_setting_repository_impl import SystemSettingRepositoryImpl
from app.data.repositories.llm_model_repository_impl import LLMModelRepositoryImpl
from app.data.repositories.calendar_credential_repository_impl import CalendarCredentialRepositoryImpl
from app.data.repositories.calendar_source_repository_impl import CalendarSourceRepositoryImpl
from app.data.repositories.tool_approval_repository_impl import ToolApprovalRepositoryImpl
from app.data.repositories.document_repository_impl import DocumentRepositoryImpl
from app.data.repositories.invite_repository_impl import InviteRepositoryImpl
from app.data.repositories.pin_reset_repository_impl import PinResetRepositoryImpl
from app.data.repositories.device_session_repository_impl import DeviceSessionRepositoryImpl

# Connectors
from app.data.connectors.searxng_search_connector import SearXNGSearchConnector
from app.data.connectors.pymupdf_document_reader import PyMuPDFDocumentReader
from app.data.connectors.caldav_calendar_connector import CalDavCalendarConnector
from app.data.connectors.google_oauth_client import GoogleOAuthClient
from app.data.security.jwt_sign_in_state_service import JwtSignInStateService
from app.data.connectors.ollama_llm_connector import OllamaLLMConnector
from app.data.connectors.ollama_embedder import OllamaEmbedder
from app.data.connectors.http_page_reader import HttpPageReader
from app.data.datasources.sqlite_source_index import SqliteSourceIndexFactory

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
from app.domain.use_cases.auth.sign_in_device import SignInDeviceUseCase
from app.domain.use_cases.auth.sign_out import SignOutUseCase
from app.domain.use_cases.auth.purge_expired_device_sessions import PurgeExpiredDeviceSessionsUseCase

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
from app.domain.use_cases.models.resolve_agent_model import ResolveAgentModelUseCase
from app.domain.use_cases.models.sync_default_model import SyncDefaultModelUseCase

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
from app.domain.use_cases.memories.list_memory_page import ListMemoryPageUseCase
from app.domain.use_cases.memories.restore_memory import RestoreMemoryUseCase
from app.domain.use_cases.memories.retire_memory import RetireMemoryUseCase
from app.domain.use_cases.memories.match_memory import MatchMemoryUseCase
from app.domain.use_cases.memories.remember_forget import ForgetUseCase, MemoryOnCardUseCase, RememberUseCase
from app.domain.use_cases.memories.answer_proposal import AnswerProposalUseCase, UndoProposalAnswerUseCase
from app.domain.use_cases.memories.withdraw_change import RestoreChangeUseCase, WithdrawChangeUseCase
from app.domain.use_cases.memories.changes_to_check import ListProposalsUseCase, LookedOverUseCase

# Integration Use Cases
from app.domain.use_cases.integrations.configure_calendar import ConfigureCalendarUseCase
from app.domain.use_cases.integrations.mark_calendar_steps_fixed import MarkCalendarStepsFixedUseCase
from app.domain.use_cases.integrations.get_user_calendar import GetUserCalendarUseCase
from app.domain.use_cases.integrations.delete_calendar import DeleteCalendarUseCase
from app.domain.use_cases.integrations.calendar_secret_resolver import CalendarSecretResolver
from app.domain.use_cases.integrations.calendar_sources import (
    MemberCalendarsUseCase,
    PickCalendarsUseCase,
    SyncCalendarSourcesUseCase,
)
from app.domain.use_cases.integrations.google_calendar_sign_in import (
    CompleteGoogleCalendarSignInUseCase,
    StartGoogleCalendarSignInUseCase,
)
from app.domain.use_cases.integrations.get_calendar_events import GetCalendarEventsUseCase
from app.domain.use_cases.integrations.create_calendar_event import CreateCalendarEventUseCase
from app.domain.use_cases.integrations.update_calendar_event import UpdateCalendarEventUseCase
from app.domain.use_cases.integrations.delete_calendar_event import DeleteCalendarEventUseCase
from app.domain.use_cases.integrations.get_calendar_event import GetCalendarEventUseCase
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

from app.domain.use_cases.chat.assemble_agent_context import AssembleAgentContextUseCase
from app.domain.use_cases.chat.tool_approval import DropPendingProposalsUseCase
from app.domain.use_cases.chat.prepare_turn import PrepareChatTurnUseCase, PrepareToolDecisionUseCase
from app.domain.use_cases.chat.process_chat_turn import ProcessChatTurnUseCase
from app.domain.use_cases.chat.tool_approval_settings import ListToolApprovalsUseCase, SetToolApprovalUseCase
from app.domain.use_cases.chat.summarize_history import SummarizeHistoryUseCase
from app.domain.use_cases.memories.reflect_chat import ReflectChatUseCase
from app.domain.use_cases.memories.reflection_sweep import ReflectionSweepUseCase
from app.data.datasources.memory_proposal_data_source import SqliteMemoryProposalDataSource
from app.data.mappers.memory_proposal_data_mapper import MemoryProposalDataMapper
from app.data.repositories.memory_proposal_repository_impl import MemoryProposalRepositoryImpl
from app.domain.use_cases.memories.embed_memories import EmbedMemoriesUseCase
from app.domain.use_cases.memories.follow_event import CheckEventMemoriesUseCase, FollowEventChangeUseCase
from app.domain.use_cases.memories.memory_block import BuildMemoryBlockUseCase
from app.domain.use_cases.memories.recall_memories import RecallMemoriesUseCase
from app.data.datasources.memory_vector_data_source import SqliteMemoryVectorDataSource
from app.data.repositories.memory_vector_repository_impl import MemoryVectorRepositoryImpl

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
_after_turn_work = AfterTurnWork()
# Whether a turn runs anywhere on the hub (memory spec §2.2). The chat locks report every turn, and
# the history summary a turn leaves behind counts as the turn still running.
_turn_activity = HubTurnActivity(after_turn_busy=lambda: _after_turn_work.running > 0)
pres_deps.get_session_lock_registry().turn_activity = _turn_activity

_user_mapper = UserDataMapper()
_space_mapper = SpaceDataMapper()
_agent_mapper = AgentDataMapper()
_session_mapper = SessionDataMapper()
_memory_mapper = MemoryDataMapper()
_memory_proposal_mapper = MemoryProposalDataMapper()
_system_setting_mapper = SystemSettingDataMapper()
_llm_model_mapper = LLMModelDataMapper()
_calendar_cred_mapper = CalendarCredentialDataMapper()
_calendar_source_mapper = CalendarSourceDataMapper()
_tool_approval_mapper = ToolApprovalDataMapper()
_document_mapper = DocumentDataMapper()
_invite_mapper = InviteDataMapper()
_pin_reset_mapper = PinResetDataMapper()
_device_session_mapper = DeviceSessionDataMapper()

_secret_cipher = SecretCipherImpl(secret_key=settings.SECRET_KEY)
_searxng_connector = SearXNGSearchConnector(
    base_url=settings.SEARXNG_BASE_URL,
    cache_ttl_seconds=settings.SEARCH_CACHE_TTL_SECONDS,
    max_cache_entries=settings.SEARXNG_CACHE_MAX_ENTRIES,
    throttle_cooldown_seconds=settings.SEARCH_THROTTLE_COOLDOWN_SECONDS,
)
_document_reader = PyMuPDFDocumentReader()
_caldav_connector = CalDavCalendarConnector()
_google_oauth = (
    GoogleOAuthClient(
        client_id=settings.GOOGLE_OAUTH_CLIENT_ID,
        client_secret=settings.GOOGLE_OAUTH_CLIENT_SECRET,
        redirect_uri=settings.google_oauth_redirect_uri,
    )
    if settings.GOOGLE_OAUTH_CLIENT_ID
    else None
)
def _build_embedder(settings) -> OllamaEmbedder:
    return OllamaEmbedder(
        base_url=settings.EMBEDDING_BASE_URL,
        model=settings.EMBEDDING_MODEL,
        timeout_seconds=settings.EMBEDDING_TIMEOUT_SECONDS,
        keep_alive=settings.EMBEDDING_KEEP_ALIVE,
    )


_sign_in_states = JwtSignInStateService(secret_key=settings.SECRET_KEY)
_ollama_connector = OllamaLLMConnector(
    base_url=settings.OLLAMA_BASE_URL,
    timeout_seconds=settings.OLLAMA_TIMEOUT_SECONDS,
)
_embedder = _build_embedder(settings)
# Each streamed turn gets a fresh index from this; nothing one turn reads is seen by another.
_source_index_factory = SqliteSourceIndexFactory(embedder=_embedder)
_page_reader = HttpPageReader(document_reader=_document_reader)



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
    llm_model_ds = SqliteLLMModelDataSource(session)
    calendar_cred_ds = SqliteCalendarCredentialDataSource(session)
    document_ds = SqliteDocumentDataSource(session)
    invite_ds = SqliteInviteDataSource(session)
    pin_reset_ds = SqlitePinResetDataSource(session)
    device_session_ds = SqliteDeviceSessionDataSource(session)

    user_repo = UserRepositoryImpl(user_ds, _user_mapper)
    space_repo = SpaceRepositoryImpl(space_ds, _space_mapper)
    agent_repo = AgentRepositoryImpl(agent_ds, _agent_mapper)
    session_repo = SessionRepositoryImpl(session_ds, _session_mapper)
    memory_repo = MemoryRepositoryImpl(memory_ds, _memory_mapper)
    system_setting_repo = SystemSettingRepositoryImpl(system_setting_ds, _system_setting_mapper)
    llm_model_repo = LLMModelRepositoryImpl(llm_model_ds, _llm_model_mapper)
    model_resolver = ResolveAgentModelUseCase(llm_model_repo)
    calendar_cred_repo = CalendarCredentialRepositoryImpl(calendar_cred_ds, _calendar_cred_mapper)
    tool_approval_repo = ToolApprovalRepositoryImpl(SqliteToolApprovalDataSource(session), _tool_approval_mapper)
    document_repo = DocumentRepositoryImpl(document_ds, _document_mapper)
    invite_repo = InviteRepositoryImpl(invite_ds, _invite_mapper)
    pin_reset_repo = PinResetRepositoryImpl(pin_reset_ds, _pin_reset_mapper)
    device_session_repo = DeviceSessionRepositoryImpl(device_session_ds, _device_session_mapper)

    uow = SqliteUnitOfWork(session)
    drop_proposals_uc = DropPendingProposalsUseCase(session_repo, uow)
    calendar_secrets = CalendarSecretResolver(calendar_cred_repo, _secret_cipher, uow, _google_oauth)
    # Every calendar of the member's account they picked (#252), kept per connection.
    calendar_source_repo = CalendarSourceRepositoryImpl(SqliteCalendarSourceDataSource(session), _calendar_source_mapper)
    calendar_sync = SyncCalendarSourcesUseCase(calendar_source_repo, _caldav_connector, uow)

    guard_code_guesses_uc = GuardCodeGuessesUseCase(system_setting_repo, uow, _code_guess_lock)
    create_member_uc = CreateMemberUseCase(user_repo, space_repo, _password_hasher, uow)
    sign_in_device_uc = SignInDeviceUseCase(device_session_repo, _jwt_token_service, uow)
    # Memory vectors and recall (memory spec §4, §5.2).
    memory_vector_repo = MemoryVectorRepositoryImpl(SqliteMemoryVectorDataSource(session))
    proposal_repo = MemoryProposalRepositoryImpl(SqliteMemoryProposalDataSource(session), _memory_proposal_mapper)
    # Event memories follow their event (memory spec §7), whichever hub route changed it.
    follow_event_uc = FollowEventChangeUseCase(
        memory_repo, proposal_repo, uow, calendar_cred_repo, _caldav_connector, calendar_secrets, sync=calendar_sync
    )
    context_assembler = AssembleAgentContextUseCase(
        memory_repo=memory_repo,
        user_repo=user_repo,
        agent_repo=agent_repo,
        history_tokens=settings.HISTORY_TOKENS,
        memory_block_tokens=settings.MEMORY_BLOCK_TOKENS,
        proposal_repo=proposal_repo,
    )
    embed_memories_uc = EmbedMemoriesUseCase(
        _embedder, memory_vector_repo, memory_repo, uow, model=settings.EMBEDDING_MODEL
    )
    deactivate_member_uc = DeactivateMemberUseCase(
        user_repo,
        space_repo,
        agent_repo,
        memory_repo,
        session_repo,
        document_repo,
        calendar_cred_repo,
        device_session_repo,
        uow,
        proposal_repo=proposal_repo,
        embed_memories=embed_memories_uc,
    )
    recall_uc = RecallMemoriesUseCase(
        embedder=_embedder,
        vector_repo=memory_vector_repo,
        memory_block=BuildMemoryBlockUseCase(
            memory_repo, user_repo=user_repo, agent_repo=agent_repo, budget_tokens=settings.MEMORY_BLOCK_TOKENS
        ),
        model=settings.EMBEDDING_MODEL,
        cutoff=settings.MEMORY_RECALL_CUTOFF,
        max_attached=settings.MEMORY_RECALL_MAX,
        wait_seconds=settings.MEMORY_RECALL_WAIT_SECONDS,
        log_text=settings.MEMORY_LOG_TEXT,
    )

    # Remember and forget (memory spec §2.1): the memory a call quotes, and what its card shows.
    memory_match = MatchMemoryUseCase(
        memory_block=BuildMemoryBlockUseCase(memory_repo, user_repo=user_repo, agent_repo=agent_repo),
        embedder=_embedder,
        vector_repo=memory_vector_repo,
        model=settings.EMBEDDING_MODEL,
        bar=settings.MEMORY_MATCH_BAR,
    )
    remember_uc = RememberUseCase(
        memory_repo=memory_repo,
        proposal_repo=proposal_repo,
        match=memory_match,
        user_repo=user_repo,
        uow=uow,
        embed_memories=embed_memories_uc,
        log_text=settings.MEMORY_LOG_TEXT,
    )
    forget_uc = ForgetUseCase(
        match=memory_match,
        retire=RetireMemoryUseCase(
            memory_repo, uow, proposal_repo=proposal_repo, session_repo=session_repo, embed_memories=embed_memories_uc
        ),
        user_repo=user_repo,
        proposal_repo=proposal_repo,
        withdraw=WithdrawChangeUseCase(proposal_repo, memory_repo, uow),
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
        page_reader=_page_reader,
        calendar_secrets=calendar_secrets,
        calendar_sources=calendar_source_repo,
        remember=remember_uc,
        forget=forget_uc,
        follow_event=follow_event_uc,
    )

    chat_turn_uc = ProcessChatTurnUseCase(
        session_repo=session_repo,
        agent_repo=agent_repo,
        llm_client=_ollama_connector,
        context_assembler=context_assembler,
        tool_executor=tool_executor,
        tool_lister=tool_lister,
        uow=uow,
        model_resolver=model_resolver,
        max_iterations=settings.MAX_TOOL_CALL_ITERATIONS,
        source_index_factory=_source_index_factory,
        context_window_tokens=settings.LLM_CONTEXT_TOKENS,
        answer_reserve_tokens=settings.ANSWER_RESERVE_TOKENS,
        approval_repo=tool_approval_repo,
        event_lookup=GetCalendarEventUseCase(calendar_cred_repo, _caldav_connector, calendar_secrets, sync=calendar_sync),
        recall=recall_uc,
        memory_card=MemoryOnCardUseCase(
            match=memory_match, forget=forget_uc, user_repo=user_repo, agent_repo=agent_repo, session_repo=session_repo
        ),
    )

    summarize_history_uc = SummarizeHistoryUseCase(
        session_repo=session_repo,
        llm_client=_ollama_connector,
        model_resolver=model_resolver,
        uow=uow,
        history_tokens=settings.HISTORY_TOKENS,
        summary_tokens=settings.HISTORY_SUMMARY_TOKENS,
    )

    async def _summarize(session_id: str, timezone_name: Optional[str] = None):
        # On a database session of its own: it runs after the turn's has been closed.
        async with AsyncSessionLocal() as bg_sess:
            bg_container = get_container(bg_sess)
            try:
                await bg_container[pres_deps.get_summarize_history_use_case].execute(
                    session_id, timezone_name=timezone_name
                )
            except Exception as sum_exc:
                logger.error("Background history summary failed for session %s: %s", session_id, sum_exc, exc_info=True)

    async def _run_after_turn(session_id: str, timezone_name: Optional[str] = None):
        """The plain `POST chat` path's history summary, queued behind the chat's earlier work."""
        _after_turn_work.spawn(session_id, lambda: _summarize(session_id, timezone_name))

    async def _run_background_chat_stream(
        session_id: str,
        current_user: User,
        content: str,
        queue: asyncio.Queue,
        regenerate: bool = False,
        timezone_name: Optional[str] = None,
        decision: Optional[Dict[str, Any]] = None,
    ):
        try:
            async with AsyncSessionLocal() as bg_sess:
                bg_container = get_container(bg_sess)
                bg_stream_uc = bg_container[pres_deps.get_process_chat_turn_use_case]
                final_event = None
                # Regenerating answers the question already in the transcript, and a decision
                # carries on the answer that paused for it; neither is handed `content` again.
                if decision is not None:
                    turn = bg_stream_uc.decide_stream(
                        session_id=session_id,
                        current_user=current_user,
                        timezone_name=timezone_name,
                        **decision,
                    )
                elif regenerate:
                    turn = bg_stream_uc.regenerate_stream(
                        session_id=session_id,
                        current_user=current_user,
                        timezone_name=timezone_name,
                    )
                else:
                    turn = bg_stream_uc.execute_stream(
                        session_id=session_id,
                        current_user=current_user,
                        content=content,
                        timezone_name=timezone_name,
                    )
                async for event in turn:
                    if event.get("type") == "done":
                        final_event = event
                    await queue.put(event)

                if final_event:
                    # Handed on rather than awaited: the chat's lock is let go when this returns,
                    # so the member can write again while the summary runs (#53). Memories are
                    # written by the reflection sweep once the chat goes quiet (memory spec §2.2).
                    _after_turn_work.spawn(session_id, lambda: _summarize(session_id, timezone_name))
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
            sign_in_device_uc,
        ),
        pres_deps.get_authenticate_token_use_case: AuthenticateTokenUseCase(
            user_repo, _jwt_token_service, device_session_repo
        ),
        pres_deps.get_list_public_members_use_case: ListPublicMembersUseCase(user_repo),
        pres_deps.get_refresh_token_use_case: RefreshTokenUseCase(
            _jwt_token_service, device_session_repo, sign_in_device_uc, uow
        ),
        pres_deps.get_sign_out_use_case: SignOutUseCase(device_session_repo, uow),
        pres_deps.get_look_up_invite_use_case: LookUpInviteUseCase(invite_repo, user_repo, guard_code_guesses_uc),
        pres_deps.get_redeem_pin_reset_use_case: RedeemPinResetUseCase(
            pin_reset_repo, user_repo, _password_hasher, uow, sign_in_device_uc, guard_code_guesses_uc, device_session_repo
        ),
        pres_deps.get_redeem_invite_use_case: RedeemInviteUseCase(
            invite_repo, user_repo, create_member_uc, sign_in_device_uc, guard_code_guesses_uc
        ),

        # Users
        pres_deps.get_list_members_use_case: ListMembersUseCase(user_repo),
        pres_deps.get_member_use_case: GetMemberUseCase(user_repo),
        pres_deps.get_update_profile_use_case: UpdateProfileUseCase(user_repo, uow),
        pres_deps.get_list_tool_approvals_use_case: ListToolApprovalsUseCase(tool_approval_repo),
        pres_deps.get_set_tool_approval_use_case: SetToolApprovalUseCase(tool_approval_repo, uow),
        pres_deps.get_change_pin_use_case: ChangePinUseCase(
            VerifyMemberPinUseCase(user_repo, _password_hasher, uow, _dummy_pin_hash, _pin_locks),
            user_repo,
            _password_hasher,
            uow,
            _jwt_token_service,
            device_session_repo,
            sign_in_device_uc,
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
        pres_deps.get_update_agent_use_case: UpdateAgentUseCase(agent_repo, uow, drop_proposals_uc),
        pres_deps.get_soft_delete_agent_use_case: SoftDeleteAgentUseCase(agent_repo, uow, drop_proposals_uc),
        pres_deps.get_restore_agent_use_case: RestoreAgentUseCase(agent_repo, uow, settings.AGENT_DELETE_GRACE_DAYS),
        pres_deps.get_list_trash_agents_use_case: ListTrashAgentsUseCase(agent_repo, settings.AGENT_DELETE_GRACE_DAYS),
        pres_deps.get_purge_trash_agent_use_case: PurgeTrashAgentUseCase(agent_repo, session_repo, uow),

        # Sessions
        pres_deps.get_list_user_sessions_use_case: ListUserSessionsUseCase(session_repo),
        pres_deps.get_session_use_case: GetSessionUseCase(session_repo),
        pres_deps.get_prepare_chat_turn_use_case: PrepareChatTurnUseCase(GetSessionUseCase(session_repo)),
        pres_deps.get_prepare_tool_decision_use_case: PrepareToolDecisionUseCase(GetSessionUseCase(session_repo)),
        pres_deps.get_create_session_use_case: CreateSessionUseCase(session_repo, agent_repo, uow),
        pres_deps.get_toggle_secret_mode_use_case: ToggleSecretModeUseCase(session_repo, uow),
        pres_deps.get_archive_session_use_case: ArchiveSessionUseCase(session_repo, uow),
        pres_deps.get_add_chat_message_use_case: AddChatMessageUseCase(session_repo, agent_repo, uow),
        pres_deps.get_delete_session_use_case: DeleteSessionUseCase(session_repo, uow),

        # Memories
        pres_deps.get_list_user_memories_use_case: ListUserMemoriesUseCase(memory_repo),
        pres_deps.get_list_household_memories_use_case: ListHouseholdMemoriesUseCase(memory_repo),
        pres_deps.get_memory_use_case: GetMemoryUseCase(memory_repo),
        pres_deps.get_create_memory_use_case: CreateMemoryUseCase(
            memory_repo, session_repo, agent_repo, uow, embed_memories=embed_memories_uc
        ),
        pres_deps.get_update_memory_use_case: UpdateMemoryUseCase(memory_repo, uow, embed_memories=embed_memories_uc),
        pres_deps.get_list_memory_page_use_case: ListMemoryPageUseCase(
            memory_repo, user_repo, agent_repo, proposal_repo=proposal_repo, session_repo=session_repo
        ),
        pres_deps.get_retire_memory_use_case: RetireMemoryUseCase(
            memory_repo, uow, proposal_repo=proposal_repo, session_repo=session_repo, embed_memories=embed_memories_uc
        ),
        pres_deps.get_restore_memory_use_case: RestoreMemoryUseCase(memory_repo, uow),
        pres_deps.get_list_proposals_use_case: ListProposalsUseCase(
            proposal_repo, memory_repo, session_repo, user_repo, agent_repo
        ),
        pres_deps.get_answer_proposal_use_case: AnswerProposalUseCase(
            proposal_repo, memory_repo, session_repo, uow, embed_memories_uc, log_text=settings.MEMORY_LOG_TEXT,
            user_repo=user_repo,
        ),
        pres_deps.get_undo_proposal_answer_use_case: UndoProposalAnswerUseCase(proposal_repo, memory_repo, uow),
        pres_deps.get_withdraw_change_use_case: WithdrawChangeUseCase(proposal_repo, memory_repo, uow),
        pres_deps.get_restore_change_use_case: RestoreChangeUseCase(proposal_repo, memory_repo, uow),
        pres_deps.get_looked_over_use_case: LookedOverUseCase(
            proposal_repo, memory_repo, session_repo, user_repo, agent_repo
        ),

        # Integrations
        pres_deps.get_configure_calendar_use_case: ConfigureCalendarUseCase(
            calendar_cred_repo, _caldav_connector, _secret_cipher, uow, mark_fixed=MarkCalendarStepsFixedUseCase(session_repo)
        ),
        pres_deps.get_user_calendar_use_case: GetUserCalendarUseCase(calendar_cred_repo),
        pres_deps.get_delete_calendar_use_case: DeleteCalendarUseCase(calendar_cred_repo, uow),
        pres_deps.get_calendar_events_use_case: GetCalendarEventsUseCase(calendar_cred_repo, _caldav_connector, calendar_secrets, sync=calendar_sync),
        pres_deps.get_member_calendars_use_case: MemberCalendarsUseCase(
            calendar_cred_repo, calendar_secrets, calendar_sync, PickCalendarsUseCase(calendar_sync, calendar_source_repo, uow)
        ),
        pres_deps.get_create_calendar_event_use_case: CreateCalendarEventUseCase(calendar_cred_repo, _caldav_connector, calendar_secrets, sync=calendar_sync),
        pres_deps.get_update_calendar_event_use_case: UpdateCalendarEventUseCase(calendar_cred_repo, _caldav_connector, calendar_secrets, follow=follow_event_uc, sync=calendar_sync),
        pres_deps.get_delete_calendar_event_use_case: DeleteCalendarEventUseCase(calendar_cred_repo, _caldav_connector, calendar_secrets, allow_agent_delete=settings.CALENDAR_ALLOW_AGENT_DELETE, follow=follow_event_uc, sync=calendar_sync),
        pres_deps.get_start_google_calendar_sign_in_use_case: StartGoogleCalendarSignInUseCase(_sign_in_states, _google_oauth),
        pres_deps.get_complete_google_calendar_sign_in_use_case: CompleteGoogleCalendarSignInUseCase(
            _sign_in_states, _google_oauth, _caldav_connector, _secret_cipher, calendar_cred_repo, uow,
            mark_fixed=MarkCalendarStepsFixedUseCase(session_repo),
        ),
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
            page_reader=_page_reader,
            calendar_secrets=calendar_secrets,
            calendar_sources=calendar_source_repo,
            follow_event=follow_event_uc,
        ),

        # Stage 3 Chat
        pres_deps.get_assemble_agent_context_use_case: context_assembler,
        pres_deps.get_process_chat_turn_use_case: chat_turn_uc,
        pres_deps.get_summarize_history_use_case: summarize_history_uc,
        pres_deps.get_after_turn_runner: _run_after_turn,
        pres_deps.get_background_chat_stream_runner: _run_background_chat_stream,
        pres_deps.get_llm_client: _ollama_connector,


        # Lifecycle & Background Maintenance
        SeedBuiltinAgentsUseCase: SeedBuiltinAgentsUseCase(agent_repo, uow),
        SyncDefaultModelUseCase: SyncDefaultModelUseCase(llm_model_repo, uow),
        EmbedMemoriesUseCase: embed_memories_uc,
        ReflectionSweepUseCase: ReflectionSweepUseCase(
            session_repo=session_repo,
            agent_repo=agent_repo,
            model_resolver=model_resolver,
            residency=_ollama_connector,
            turn_activity=_turn_activity,
            reflect_chat=ReflectChatUseCase(
                llm_client=_ollama_connector,
                session_repo=session_repo,
                memory_repo=memory_repo,
                proposal_repo=proposal_repo,
                user_repo=user_repo,
                memory_block=BuildMemoryBlockUseCase(memory_repo, user_repo=user_repo, agent_repo=agent_repo),
                embedder=_embedder,
                vector_repo=memory_vector_repo,
                embed_memories=embed_memories_uc,
                uow=uow,
                embedding_model=settings.EMBEDDING_MODEL,
                neighbour_bar=settings.REFLECTION_NEIGHBOUR_BAR,
                neighbours_max=settings.REFLECTION_NEIGHBOURS_MAX,
                log_text=settings.MEMORY_LOG_TEXT,
                event_lookup=GetCalendarEventUseCase(calendar_cred_repo, _caldav_connector, calendar_secrets, sync=calendar_sync),
            ),
            embed_memories=embed_memories_uc,
            idle_minutes=settings.REFLECTION_IDLE_MINUTES,
        ),
        CheckEventMemoriesUseCase: CheckEventMemoriesUseCase(memory_repo, follow_event_uc),
        PurgeExpiredTrashAgentsUseCase: PurgeExpiredTrashAgentsUseCase(agent_repo, session_repo, uow, settings.AGENT_DELETE_GRACE_DAYS),
        PurgeExpiredDeviceSessionsUseCase: PurgeExpiredDeviceSessionsUseCase(
            device_session_repo, uow, timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
        ),
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
        pres_deps.get_sign_out_use_case,
        pres_deps.get_look_up_invite_use_case,
        pres_deps.get_redeem_invite_use_case,
        pres_deps.get_approve_pin_reset_use_case,
        pres_deps.get_redeem_pin_reset_use_case,
        pres_deps.get_list_members_use_case,
        pres_deps.get_member_use_case,
        pres_deps.get_update_profile_use_case,
        pres_deps.get_list_tool_approvals_use_case,
        pres_deps.get_set_tool_approval_use_case,
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
        pres_deps.get_prepare_chat_turn_use_case,
        pres_deps.get_prepare_tool_decision_use_case,
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
        pres_deps.get_list_memory_page_use_case,
        pres_deps.get_retire_memory_use_case,
        pres_deps.get_restore_memory_use_case,
        pres_deps.get_list_proposals_use_case,
        pres_deps.get_answer_proposal_use_case,
        pres_deps.get_undo_proposal_answer_use_case,
        pres_deps.get_withdraw_change_use_case,
        pres_deps.get_restore_change_use_case,
        pres_deps.get_looked_over_use_case,
        pres_deps.get_configure_calendar_use_case,
        pres_deps.get_user_calendar_use_case,
        pres_deps.get_delete_calendar_use_case,
        pres_deps.get_calendar_events_use_case,
        pres_deps.get_member_calendars_use_case,
        pres_deps.get_create_calendar_event_use_case,
        pres_deps.get_update_calendar_event_use_case,
        pres_deps.get_delete_calendar_event_use_case,
        pres_deps.get_start_google_calendar_sign_in_use_case,
        pres_deps.get_complete_google_calendar_sign_in_use_case,
        pres_deps.get_execute_search_use_case,
        pres_deps.get_parse_pdf_document_use_case,
        pres_deps.get_save_document_use_case,
        pres_deps.get_document_use_case,
        pres_deps.get_list_documents_use_case,
        pres_deps.get_delete_document_use_case,
        pres_deps.get_list_available_tools_use_case,
        pres_deps.get_execute_tool_use_case,
        pres_deps.get_assemble_agent_context_use_case,
        pres_deps.get_process_chat_turn_use_case,
        pres_deps.get_after_turn_runner,
        pres_deps.get_background_chat_stream_runner,
        pres_deps.get_llm_client,
    ]:

        def make_provider(target_stub):
            async def provider(container: dict = Depends(get_request_container)):
                return container[target_stub]
            return provider

        app.dependency_overrides[stub_fn] = make_provider(stub_fn)

