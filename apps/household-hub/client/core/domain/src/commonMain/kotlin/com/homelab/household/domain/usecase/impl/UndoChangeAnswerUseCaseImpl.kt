package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.repository.MemoryRepository
import com.homelab.household.domain.usecase.UndoChangeAnswerUseCase

class UndoChangeAnswerUseCaseImpl(
    private val memoryRepository: MemoryRepository,
) : UndoChangeAnswerUseCase {
    override suspend operator fun invoke(changeId: String) = memoryRepository.undoChangeAnswer(changeId)
}
