package com.homelab.household.di

import com.homelab.household.domain.usecase.ArchiveSessionUseCase
import com.homelab.household.domain.usecase.ApproveToolProposalUseCase
import com.homelab.household.domain.usecase.AuditMemoriesUseCase
import com.homelab.household.domain.usecase.CheckAuthStatusUseCase
import com.homelab.household.domain.usecase.CheckServerHealthUseCase
import com.homelab.household.domain.usecase.CreateAgentUseCase
import com.homelab.household.domain.usecase.CreateSessionUseCase
import com.homelab.household.domain.usecase.DeleteAgentUseCase
import com.homelab.household.domain.usecase.DeleteSessionUseCase
import com.homelab.household.domain.usecase.FirstRunOnboardUseCase
import com.homelab.household.domain.usecase.GetAgentUseCase
import com.homelab.household.domain.usecase.GetCurrentUserUseCase
import com.homelab.household.domain.usecase.GetHubHostUseCase
import com.homelab.household.domain.usecase.HasStoredSessionUseCase
import com.homelab.household.domain.usecase.GetHouseholdSpaceUseCase
import com.homelab.household.domain.usecase.GetPersonalSpaceUseCase
import com.homelab.household.domain.usecase.GetSessionUseCase
import com.homelab.household.domain.usecase.ListAgentsUseCase
import com.homelab.household.domain.usecase.ListHouseholdMilestonesUseCase
import com.homelab.household.domain.usecase.ListSessionsUseCase
import com.homelab.household.domain.usecase.ListUserAuditMilestonesUseCase
import com.homelab.household.domain.usecase.ListMembersUseCase
import com.homelab.household.domain.usecase.LockSecretSessionsUseCase
import com.homelab.household.domain.usecase.LoginUseCase
import com.homelab.household.domain.usecase.LogoutUseCase
import com.homelab.household.domain.usecase.ObserveCurrentUserUseCase
import com.homelab.household.domain.usecase.ObserveMessagesUseCase
import com.homelab.household.domain.usecase.ObserveServerStatusUseCase
import com.homelab.household.domain.usecase.RestoreAgentUseCase
import com.homelab.household.domain.usecase.RetryMessageUseCase
import com.homelab.household.domain.usecase.RevokeMemoryUseCase
import com.homelab.household.domain.usecase.RevokeMilestoneUseCase
import com.homelab.household.domain.usecase.StreamChatTurnUseCase
import com.homelab.household.domain.usecase.ToggleSecretModeUseCase
import com.homelab.household.domain.usecase.UnlockSecretSessionUseCase
import com.homelab.household.domain.usecase.UpdateAgentUseCase
import com.homelab.household.domain.usecase.UpdateMemoryUseCase
import com.homelab.household.domain.usecase.UpdateSpaceSettingsUseCase
import com.homelab.household.domain.usecase.impl.ApproveToolProposalUseCaseImpl
import com.homelab.household.domain.usecase.impl.ArchiveSessionUseCaseImpl
import com.homelab.household.domain.usecase.impl.AuditMemoriesUseCaseImpl
import com.homelab.household.domain.usecase.impl.CheckAuthStatusUseCaseImpl
import com.homelab.household.domain.usecase.impl.CheckServerHealthUseCaseImpl
import com.homelab.household.domain.usecase.impl.CreateAgentUseCaseImpl
import com.homelab.household.domain.usecase.impl.CreateSessionUseCaseImpl
import com.homelab.household.domain.usecase.impl.DeleteAgentUseCaseImpl
import com.homelab.household.domain.usecase.impl.DeleteSessionUseCaseImpl
import com.homelab.household.domain.usecase.impl.FirstRunOnboardUseCaseImpl
import com.homelab.household.domain.usecase.impl.GetAgentUseCaseImpl
import com.homelab.household.domain.usecase.impl.GetCurrentUserUseCaseImpl
import com.homelab.household.domain.usecase.impl.GetHouseholdSpaceUseCaseImpl
import com.homelab.household.domain.usecase.impl.GetHubHostUseCaseImpl
import com.homelab.household.domain.usecase.impl.GetPersonalSpaceUseCaseImpl
import com.homelab.household.domain.usecase.impl.GetSessionUseCaseImpl
import com.homelab.household.domain.usecase.impl.HasStoredSessionUseCaseImpl
import com.homelab.household.domain.usecase.impl.ListAgentsUseCaseImpl
import com.homelab.household.domain.usecase.impl.ListHouseholdMilestonesUseCaseImpl
import com.homelab.household.domain.usecase.impl.ListMembersUseCaseImpl
import com.homelab.household.domain.usecase.impl.ListSessionsUseCaseImpl
import com.homelab.household.domain.usecase.impl.ListUserAuditMilestonesUseCaseImpl
import com.homelab.household.domain.usecase.impl.LockSecretSessionsUseCaseImpl
import com.homelab.household.domain.usecase.impl.LoginUseCaseImpl
import com.homelab.household.domain.usecase.impl.LogoutUseCaseImpl
import com.homelab.household.domain.usecase.impl.ObserveCurrentUserUseCaseImpl
import com.homelab.household.domain.usecase.impl.ObserveMessagesUseCaseImpl
import com.homelab.household.domain.usecase.impl.ObserveServerStatusUseCaseImpl
import com.homelab.household.domain.usecase.impl.RestoreAgentUseCaseImpl
import com.homelab.household.domain.usecase.impl.RetryMessageUseCaseImpl
import com.homelab.household.domain.usecase.impl.RevokeMemoryUseCaseImpl
import com.homelab.household.domain.usecase.impl.RevokeMilestoneUseCaseImpl
import com.homelab.household.domain.usecase.impl.StreamChatTurnUseCaseImpl
import com.homelab.household.domain.usecase.impl.ToggleSecretModeUseCaseImpl
import com.homelab.household.domain.usecase.impl.UnlockSecretSessionUseCaseImpl
import com.homelab.household.domain.usecase.impl.UpdateAgentUseCaseImpl
import com.homelab.household.domain.usecase.impl.UpdateMemoryUseCaseImpl
import com.homelab.household.domain.usecase.impl.UpdateSpaceSettingsUseCaseImpl
import org.koin.dsl.module

val domainModule = module {
    // Session use cases
    factory<CreateSessionUseCase> { CreateSessionUseCaseImpl(get()) }
    factory<GetSessionUseCase> { GetSessionUseCaseImpl(get()) }
    factory<ListSessionsUseCase> { ListSessionsUseCaseImpl(get()) }
    factory<ArchiveSessionUseCase> { ArchiveSessionUseCaseImpl(get()) }
    factory<ToggleSecretModeUseCase> { ToggleSecretModeUseCaseImpl(get()) }
    factory<DeleteSessionUseCase> { DeleteSessionUseCaseImpl(get()) }
    factory<ApproveToolProposalUseCase> { ApproveToolProposalUseCaseImpl(get()) }
    factory<ObserveMessagesUseCase> { ObserveMessagesUseCaseImpl(get()) }
    factory<RetryMessageUseCase> { RetryMessageUseCaseImpl(get()) }
    factory<StreamChatTurnUseCase> { StreamChatTurnUseCaseImpl(get()) }

    // Auth use cases
    factory<LoginUseCase> { LoginUseCaseImpl(get()) }
    factory<FirstRunOnboardUseCase> { FirstRunOnboardUseCaseImpl(get()) }
    factory<ListMembersUseCase> { ListMembersUseCaseImpl(get()) }
    factory<CheckAuthStatusUseCase> { CheckAuthStatusUseCaseImpl(get()) }
    factory<GetCurrentUserUseCase> { GetCurrentUserUseCaseImpl(get()) }
    factory<LogoutUseCase> { LogoutUseCaseImpl(get()) }
    factory<ObserveCurrentUserUseCase> { ObserveCurrentUserUseCaseImpl(get()) }
    factory<GetHubHostUseCase> { GetHubHostUseCaseImpl(get()) }
    factory<HasStoredSessionUseCase> { HasStoredSessionUseCaseImpl(get()) }

    // Server status use cases
    factory<ObserveServerStatusUseCase> { ObserveServerStatusUseCaseImpl(get()) }
    factory<CheckServerHealthUseCase> { CheckServerHealthUseCaseImpl(get()) }

    // Secret lock use cases
    factory<LockSecretSessionsUseCase> { LockSecretSessionsUseCaseImpl(get()) }
    factory<UnlockSecretSessionUseCase> { UnlockSecretSessionUseCaseImpl(get()) }

    // Gossip & Memory use cases
    factory<ListHouseholdMilestonesUseCase> { ListHouseholdMilestonesUseCaseImpl(get()) }
    factory<ListUserAuditMilestonesUseCase> { ListUserAuditMilestonesUseCaseImpl(get()) }
    factory<RevokeMilestoneUseCase> { RevokeMilestoneUseCaseImpl(get()) }
    factory<AuditMemoriesUseCase> { AuditMemoriesUseCaseImpl(get()) }
    factory<RevokeMemoryUseCase> { RevokeMemoryUseCaseImpl(get()) }
    factory<UpdateMemoryUseCase> { UpdateMemoryUseCaseImpl(get()) }

    // Space & Agent use cases
    factory<GetPersonalSpaceUseCase> { GetPersonalSpaceUseCaseImpl(get()) }
    factory<GetHouseholdSpaceUseCase> { GetHouseholdSpaceUseCaseImpl(get()) }
    factory<UpdateSpaceSettingsUseCase> { UpdateSpaceSettingsUseCaseImpl(get()) }
    factory<ListAgentsUseCase> { ListAgentsUseCaseImpl(get()) }
    factory<GetAgentUseCase> { GetAgentUseCaseImpl(get()) }
    factory<CreateAgentUseCase> { CreateAgentUseCaseImpl(get()) }
    factory<UpdateAgentUseCase> { UpdateAgentUseCaseImpl(get()) }
    factory<DeleteAgentUseCase> { DeleteAgentUseCaseImpl(get()) }
    factory<RestoreAgentUseCase> { RestoreAgentUseCaseImpl(get()) }
}
