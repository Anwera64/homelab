package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.HouseholdMilestone

interface ListUserAuditMilestonesUseCase {
    suspend operator fun invoke(limit: Int = 50): List<HouseholdMilestone>
}
