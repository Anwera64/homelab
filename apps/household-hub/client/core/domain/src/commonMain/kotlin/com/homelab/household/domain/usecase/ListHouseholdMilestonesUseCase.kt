package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.HouseholdMilestone

interface ListHouseholdMilestonesUseCase {
    suspend operator fun invoke(limit: Int = 20): List<HouseholdMilestone>
}
