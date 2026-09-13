package com.homelab.household.domain.usecase

fun interface RevokeMilestoneUseCase {
    suspend operator fun invoke(milestoneId: String)
}
