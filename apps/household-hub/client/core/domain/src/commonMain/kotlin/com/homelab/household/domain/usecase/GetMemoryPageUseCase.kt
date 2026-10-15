package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.MemoryPage

/** The memory page: your memories and the household's. */
fun interface GetMemoryPageUseCase {
    suspend operator fun invoke(): MemoryPage
}
