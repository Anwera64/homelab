package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.model.ProposalAnswer
import com.homelab.household.domain.repository.MemoryRepository
import com.homelab.household.domain.usecase.AnswerChangeUseCase

class AnswerChangeUseCaseImpl(
    private val memoryRepository: MemoryRepository,
) : AnswerChangeUseCase {
    override suspend operator fun invoke(
        changeId: String,
        answer: ProposalAnswer,
    ) = memoryRepository.answerChange(changeId, answer)
}
