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
import org.koin.dsl.module

val domainModule = module {
    // Session use cases
    factory { CreateSessionUseCase(get()) }
    factory { GetSessionUseCase(get()) }
    factory { ListSessionsUseCase(get()) }
    factory { ArchiveSessionUseCase(get()) }
    factory { ToggleSecretModeUseCase(get()) }
    factory { DeleteSessionUseCase(get()) }
    factory { ApproveToolProposalUseCase(get()) }
    factory { ObserveMessagesUseCase(get()) }
    factory { RetryMessageUseCase(get()) }
    factory { StreamChatTurnUseCase(get()) }

    // Auth use cases
    factory { LoginUseCase(get()) }
    factory { FirstRunOnboardUseCase(get()) }
    factory { ListMembersUseCase(get()) }
    factory { CheckAuthStatusUseCase(get()) }
    factory { GetCurrentUserUseCase(get()) }
    factory { LogoutUseCase(get()) }
    factory { ObserveCurrentUserUseCase(get()) }
    factory { GetHubHostUseCase(get()) }
    factory { HasStoredSessionUseCase(get()) }

    // Server status use cases
    factory { ObserveServerStatusUseCase(get()) }
    factory { CheckServerHealthUseCase(get()) }

    // Secret lock use cases
    factory { LockSecretSessionsUseCase(get()) }
    factory { UnlockSecretSessionUseCase(get()) }

    // Gossip & Memory use cases
    factory { ListHouseholdMilestonesUseCase(get()) }
    factory { ListUserAuditMilestonesUseCase(get()) }
    factory { RevokeMilestoneUseCase(get()) }
    factory { AuditMemoriesUseCase(get()) }
    factory { RevokeMemoryUseCase(get()) }
    factory { UpdateMemoryUseCase(get()) }

    // Space & Agent use cases
    factory { GetPersonalSpaceUseCase(get()) }
    factory { GetHouseholdSpaceUseCase(get()) }
    factory { UpdateSpaceSettingsUseCase(get()) }
    factory { ListAgentsUseCase(get()) }
    factory { GetAgentUseCase(get()) }
    factory { CreateAgentUseCase(get()) }
    factory { UpdateAgentUseCase(get()) }
    factory { DeleteAgentUseCase(get()) }
    factory { RestoreAgentUseCase(get()) }
}
